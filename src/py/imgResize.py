import argparse
import shutil
import tempfile
from pathlib import Path

import boto3
import botostubs
from wand.image import Image

# IMPORTANT: s3 credentials must be configured!
# Read https://boto3.amazonaws.com/v1/documentation/api/latest/guide/credentials.html#guide-credentials
# for instructions on how to pass them to the script


# TODO split into two programs: find-missing and generate-new

sizes = {"thumbview": (400, 300),
         "low": (800, 600),
         "med": (1200, 900),
         "high": (2000, 1600)}

parser = argparse.ArgumentParser(description='Generate smaller versions of specimen images')

output = parser.add_mutually_exclusive_group(required=True)

parser.add_argument("--source-dir", type=Path,
                    help='directory to check for existing images (defaults to out-folder, if given)')

output.add_argument('--out-folder', type=Path, metavar="DIRECTORY", help='destination for generated images')
# output.add_argument('--upload', action='store_true',
#                     help='upload new files directly to s3 bucket intead of saving to disk')

parser.add_argument('--bucket-prefix', type=Path, dest='bucket_prefix', default='images',
                    help='s3_bucket prefix for images (default: %(default)s)')

parser.add_argument('--only-missing', action='store_true', help='only regenerate missing images')

parser.add_argument('--subdir', action='store_true',
                    help='create subdirectories for each specimen code inside the destination folder')

specimen_targets = parser.add_mutually_exclusive_group(required=True)

specimen_targets.add_argument('--specimens', type=str, nargs='+', metavar='code',
                              help='specimen codes to generate images for')

specimen_targets.add_argument('--all-missing', action='store_true',
                              help='search for all empty images in the bucket and process them')


def find_original_images(specimen_code: str, s3_bucket: botostubs.S3.S3Resource.Bucket,
                         image_bucket_prefix: Path = Path('images')) -> list[botostubs.S3.S3Resource.ObjectSummary]:
    """
    Finds the original tiff or jpg images for a specimen code.

    Returns:
        A list of s3 objects, one for each original image
    """

    # images/specimen_code_here     note the lack of leading slash
    prefix = image_bucket_prefix.joinpath(specimen_code.lower())

    # remove a leading slash just in case
    prefix = str(prefix).lstrip('/')

    response = s3_bucket.objects.filter(Prefix=prefix)

    original_images = []

    for item in response:
        file_name = Path(item.key).stem

        if file_name.isupper():
            original_images.append(item)

    return original_images


def download_images_to_folder(image_objects: list[botostubs.S3.S3Resource.ObjectSummary], download_dir: Path = None,
                              source_dir: Path = None, replace_existing=False) -> list[Path]:
    if download_dir is None:
        download_dir = Path(tempfile.mkdtemp())

    image_paths = []

    for image in image_objects:
        image_filename = image.key.split('/')[-1]

        source_filepath = source_dir.joinpath(image_filename)

        image_filepath = download_dir.joinpath(image_filename)

        # Convert from objectSummary to Object to get file info and download
        image_obj = image.Object()

        # Add to list first so that we can skip downloading if necessary
        image_paths.append(image_filepath)

        if image_filepath.exists():
            if replace_existing:
                # If we want to overwrite, but files are identical, don't bother
                if image_filepath.stat().st_size == image_obj.content_length:
                    print(f"Original image is identical: {image_filepath} - not downloading")
                    continue
            else:
                print(f"Original file exists: {image_filepath} - not downloading")
                continue

        else:
            if source_filepath.exists():
                if source_filepath.stat().st_size == image_obj.content_length:
                    shutil.copy(source_filepath, image_filepath)
                    continue

        print(f"Downloading image {image_filename}")
        image_obj.download_file(str(image_filepath))

    return image_paths


def generate_file_names(input_image_path: Path) -> dict[str, (int, int)]:
    """
    Generate file names given the filepath to an original TIF or JPG

    Returns:
        A dictionary of filepath: (width, height) pairs.
    """
    image_sizes = {}

    for suffix, (width, height) in sizes.items():
        image_base = input_image_path.stem.lower()

        if not image_base[-1].isnumeric():
            image_base += "_1"

        new_image_name = f"{image_base}_{suffix}.jpg"
        image_sizes[new_image_name] = (width, height)

    return image_sizes


def resize_image_as_filestream(input_image: Image, width: int, height: int) -> Image:
    """
    Clones an image and resizes it.

    Aspect ratio is maintained, shrunken until both width and height are <= specified values.

    Returns:
        The resized image
    """
    with input_image.clone() as new_image:
        new_image.transform(resize=f"{width}x{height}>")
        new_image.strip()
        return new_image


def resize_image(input_image_path: Path, out_dir: Path, only_create_missing: bool = False):
    # The images to create, and their dimensions. keys are image names, values are (width, height) tuples
    create_sizes = {}

    for suffix, (width, height) in sizes.items():
        image_base = input_image_path.stem.lower()

        # If the source image doesn't have a number at the end, set it to one
        if not image_base[-1].isnumeric():
            image_base += "_1"

        new_image_name = f"{image_base}_{suffix}.jpg"

        new_image_filepath = out_dir.joinpath(new_image_name)

        # If only creating missing images, skip any images that exist
        if not new_image_filepath.exists() or new_image_filepath.stat().st_size == 0 or not only_create_missing:
            create_sizes[new_image_name] = (width, height)

    # If we added any files to be resized, open the image
    if create_sizes:
        with Image(filename=input_image_path) as original_image:
            for new_image_name, (width, height) in create_sizes.items():
                new_image_filepath = out_dir.joinpath(new_image_name)

                with original_image.clone() as new_image:
                    new_image.transform(resize=f"{width}x{height}>")
                    new_image.strip()
                    print(f"Creating image {new_image_name}")
                    new_image.save(filename=new_image_filepath)


def find_empty_files(bucket, prefix: str = '') -> list[str]:
    empty_files = []
    for obj in bucket.objects.filter(Prefix=prefix):
        if obj.size == 0 and obj.key.endswith(".jpg"):
            empty_files.append(obj.key)
            print(obj.key)

    return empty_files


# resize_image(Path(sourcefile), Path("."))

if __name__ == '__main__':
    args = parser.parse_args()

    s3_config = {
        "region_name": 'sfo3',
        "endpoint_url": 'https://sfo3.digitaloceanspaces.com',
    }

    session = boto3.session.Session()

    client = session.client('s3', **s3_config)
    s3_bucket = boto3.resource('s3', **s3_config).Bucket('antweb')

    # if args.out_folder is not None:
    out_dir: Path = args.out_folder.resolve()

    if args.source_dir is None:
        input_folder: Path = args.out_folder
    else:
        input_folder: Path = args.source_dir.resolve()

    specimen_codes = []

    if args.all_missing:
        print("Finding empty images")
        empty_keys = find_empty_files(s3_bucket, str(args.bucket_prefix))

        # Get the parent dir of the file, which is specimen code
        specimen_codes = [str(Path(key).parent.name) for key in empty_keys]

    else:
        specimen_codes = args.specimens

    for code in specimen_codes:
        images: list[botostubs.S3.S3Resource.ObjectSummary] = find_original_images(code, s3_bucket, args.bucket_prefix)

        # If subdir enabled or uploading to s3 bucket, create subdirs
        # if args.subdir or args.upload:
        output_folder = out_dir
        if args.subdir:
            output_folder = out_dir.joinpath(code)

        # Create starting directory if needed
        output_folder.mkdir(parents=True, exist_ok=True)

        image_files = download_images_to_folder(images, output_folder, input_folder, replace_existing=False)

        for image_path in image_files:
            output_folder.mkdir(parents=True, exist_ok=True)

            resize_image(image_path, output_folder, args.only_missing)
