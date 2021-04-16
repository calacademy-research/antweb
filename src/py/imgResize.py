import argparse
import tempfile
from pathlib import Path

import boto3
import botostubs
from wand.image import Image

sizes = {"thumbview": (400, 300),
         "low": (800, 600),
         "med": (1200, 900),
         "high": (2000, 1600)}

parser = argparse.ArgumentParser(description='Generate smaller versions of specimen images')

output = parser.add_mutually_exclusive_group(required=True)

parser.add_argument("--source-dir", type=Path, help='directory to check for existing images')

output.add_argument('--out-folder', type=Path, metavar="DIRECTORY", help='destination for generated images')
output.add_argument('--upload', action='store_true',
                    help='upload new files directly to s3 bucket intead of saving to disk')

parser.add_argument('--bucket-prefix', type=Path, dest='bucket_prefix', default='images',
                    help='s3_bucket prefix for images (default: %(default)s)')

parser.add_argument('--only-missing', action='store_true', help='only generate images for missing images')

parser.add_argument('--subdir', action='store_true',
                    help='create subdirectories for each specimen code inside the destination folder')

parser.add_argument('specimen_codes', type=str, nargs='+', metavar='specimen code',
                    help='specimen codes to generate images for')


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

        if source_filepath.exists():
            if replace_existing:
                # If we want to overwrite, but files are identical, don't bother
                if image_filepath.stat().st_size == image_obj.content_length:
                    print(f"Original image is identical: {image_filepath} - not downloading")
                    continue
            else:
                print(f"Original file exists: {image_filepath} - not downloading")
                continue

        image_obj.download_file(str(image_filepath))

    return image_paths

def resize_image(input_image_path: Path, out_dir: Path, only_create_missing: bool = False):
    with Image(filename=input_image_path) as original_image:
        for suffix, (width, height) in sizes.items():
            image_base = input_image_path.stem.lower()

            # If the source image doesn't have a number at the end, set it to one
            if not image_base[-1].isnumeric():
                image_base += "_1"

            new_image_name = f"{image_base}_{suffix}.jpg"

            new_image_filepath = out_dir.joinpath(new_image_name)

            # If only creating missing images, skip any images that exist
            if new_image_filepath.exists() and new_image_filepath.stat().st_size != 0:
                if only_create_missing:
                    continue

            with original_image.clone() as new_image:
                new_image.transform(resize=f"{width}x{height}>")
                new_image.strip()
                print(f"Creating image {new_image_name}")
                new_image.save(filename=new_image_filepath)

if __name__ == '__main__':
    args = parser.parse_args()


    session = boto3.session.Session()

    client = session.client('s3', **s3_config)
    s3_bucket = boto3.resource('s3', **s3_config).Bucket('antweb')

    input_folder: Path = args.source_dir.resolve()
    output_folder: Path = args.out_folder.resolve()

    for code in args.specimen_codes:
        images: list[botostubs.S3.S3Resource.ObjectSummary] = find_original_images(code, s3_bucket, args.bucket_prefix)

        # If subdir enabled or uploading to s3 bucket, create subdirs
        if args.subdir or args.upload:
            output_folder = output_folder.joinpath(code)

        image_files = download_images_to_folder(images, output_folder, input_folder, replace_existing=False)

        for image_path in image_files:
            output_folder.mkdir(parents=True, exist_ok=True)

            resize_image(image_path, output_folder, args.only_missing)
