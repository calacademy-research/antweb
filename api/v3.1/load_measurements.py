#!/usr/bin/env python
"""
Load a myrmetrics release TSV into the AntWeb `measurement` table.

Runs inside the api container (it already has PyMySQL and network access to mysql):

    docker cp myrmetrics_release_1.0.tsv antweb_api_1:/tmp/
    docker exec antweb_api_1 python load_measurements.py /tmp/myrmetrics_release_1.0.tsv --dry-run
    docker exec antweb_api_1 python load_measurements.py /tmp/myrmetrics_release_1.0.tsv

What it does:
  1. Checks the header against the expected column list (SCHEMA.md).
  2. Checks enum columns (view, status, flag_reason, scale sources); stops on unknown values.
  3. Checks every specimen_code exists in the AntWeb specimen table. Rows whose code is
     missing are written to <input>.held.tsv and NOT loaded.
  4. Refuses to load a release that already has rows unless --replace is given, in which
     case that release's rows are deleted and reloaded in one transaction.

Python 3.7 compatible (the api image is python:3.7).
"""
import argparse
import csv
import os
import sys

import pymysql

DB = dict(host=os.environ.get('AW_DB_HOST', 'mysql'), port=int(os.environ.get('AW_DB_PORT', '3306')),
          user=os.environ.get('AW_DB_USER', 'antweb'), password=os.environ.get('AW_DB_PASS', 'f0rm1c6'),
          db=os.environ.get('AW_DB_NAME', 'ant'), charset='utf8')

KP_COLS = []
for _i in range(1, 7):
    KP_COLS += ['kp%d_name' % _i, 'kp%d_x' % _i, 'kp%d_y' % _i, 'kp%d_conf' % _i]

# TSV column order per SCHEMA.md (release 1.0 draft)
TSV_COLS = (['release', 'record_id', 'specimen_code', 'image_filename', 'image_url', 'view',
             'image_width', 'image_height', 'trait', 'trait_name', 'value', 'unit', 'value_px',
             'trait_kp_a', 'trait_kp_b', 'scale_value_raw', 'scale_unit_raw', 'scale_mm',
             'scale_value_source', 'scale_bar_px', 'scale_bar_source', 'px_per_mm']
            + KP_COLS
            + ['n_detections', 'keypoints_corrected', 'scale_corrected', 'qa_kp_ok', 'qa_ruler_ok',
               'qa_missing_head', 'qa_missing_gaster', 'status', 'flag_reason', 'flag_note',
               'pose_model', 'ruler_model', 'contributor', 'record_date'])

# TSV name -> DB column name (release and view are MySQL reserved words)
DB_NAME = {'release': 'data_release', 'view': 'image_view'}

FLOAT_COLS = {'value', 'value_px', 'scale_value_raw', 'scale_mm', 'scale_bar_px', 'px_per_mm'} | \
             {c for c in KP_COLS if not c.endswith('_name')}
INT_COLS = {'image_width', 'image_height', 'n_detections'}
BOOL_COLS = {'keypoints_corrected', 'scale_corrected', 'qa_kp_ok', 'qa_ruler_ok',
             'qa_missing_head', 'qa_missing_gaster'}

ENUMS = {
    'view': {'dorsal', 'head', 'profile'},
    'trait': {'PW', 'BL', 'HW', 'HL', 'ML', 'WL', 'HLP'},
    'status': {'automated', 'reviewed', 'flagged'},
    'flag_reason': {'', 'scale_bar_view_unresolved', 'scale_bar_mislabeled'},
    'scale_value_source': {'', 'ocr_llm_agree', 'llm_reconciled', 'ocr_fallback', 'manual', 'manual_unreadable'},
    'scale_bar_source': {'', 'method_h', 'contour', 'manual', 'manual_unreadable'},
}

BATCH = 2000


def convert(col, val):
    if val is None or val == '':
        return None
    if col in FLOAT_COLS:
        return float(val)
    if col in INT_COLS:
        return int(float(val))
    if col in BOOL_COLS:
        v = val.strip().lower()
        if v in ('true', '1', 'yes', 't'):
            return 1
        if v in ('false', '0', 'no', 'f'):
            return 0
        raise ValueError('bad boolean %r in %s' % (val, col))
    return val


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('tsv')
    ap.add_argument('--dry-run', action='store_true', help='validate only, write nothing')
    ap.add_argument('--replace', action='store_true', help='delete and reload rows for this release')
    args = ap.parse_args()

    with open(args.tsv, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f, delimiter='\t')
        header = reader.fieldnames or []
        missing = [c for c in TSV_COLS if c not in header]
        extra = [c for c in header if c not in TSV_COLS]
        if missing:
            sys.exit('STOP: columns missing from TSV: %s' % ', '.join(missing))
        if extra:
            print('note: extra TSV columns ignored: %s' % ', '.join(extra))
        rows = list(reader)

    print('rows read: %d' % len(rows))
    if not rows:
        sys.exit('STOP: no rows')

    # enums and basic consistency
    bad = {}
    for n, r in enumerate(rows, start=2):
        for col, allowed in ENUMS.items():
            if r[col] not in allowed:
                bad.setdefault((col, r[col]), []).append(n)
        if r['flag_reason'] and r['status'] != 'flagged':
            bad.setdefault(('flag_reason set but status not flagged', r['status']), []).append(n)
    if bad:
        print('STOP: values outside the schema (column, value: count, first lines):')
        for (col, v), lines in sorted(bad.items()):
            print('  %s = %r: %d rows, e.g. lines %s' % (col, v, len(lines), lines[:5]))
        sys.exit(1)

    releases = sorted({r['release'] for r in rows})
    if len(releases) != 1:
        sys.exit('STOP: file has more than one release: %s' % releases)
    release = releases[0]

    ids = [r['record_id'] for r in rows]
    if len(ids) != len(set(ids)):
        sys.exit('STOP: duplicate record_id values in file')

    # convert types
    try:
        values = []
        for r in rows:
            values.append(tuple(convert(c, r[c]) for c in TSV_COLS))
    except ValueError as e:
        sys.exit('STOP: %s' % e)

    conn = pymysql.connect(**DB)
    cur = conn.cursor()

    # specimen codes must exist
    codes = sorted({r['specimen_code'] for r in rows})
    found = set()
    for i in range(0, len(codes), 1000):
        chunk = codes[i:i + 1000]
        cur.execute('SELECT code FROM specimen WHERE code IN (%s)' % ','.join(['%s'] * len(chunk)), chunk)
        found.update(c[0] for c in cur.fetchall())
    # MySQL compares codes case-insensitively, so match the same way here
    lower_found = {c.lower() for c in found}
    held_codes = {c for c in codes if c.lower() not in lower_found}
    keep = [v for v, r in zip(values, rows) if r['specimen_code'] not in held_codes]
    held = [r for r in rows if r['specimen_code'] in held_codes]
    print('specimens in file: %d; not found on AntWeb: %d (rows held: %d)' % (len(codes), len(held_codes), len(held)))

    if held:
        held_path = args.tsv + '.held.tsv'
        try:
            with open(held_path, 'w', newline='', encoding='utf-8') as f:
                w = csv.DictWriter(f, fieldnames=TSV_COLS, delimiter='\t', extrasaction='ignore')
                w.writeheader()
                w.writerows(held)
            print('held rows written to %s' % held_path)
        except OSError as e:
            print('could not write held file: %s' % e)

    cur.execute('SELECT COUNT(*) FROM measurement WHERE data_release = %s', (release,))
    existing = cur.fetchone()[0]
    print('release %s: %d rows already in measurement' % (release, existing))
    if existing and not args.replace:
        sys.exit('STOP: release %s already loaded. Use --replace to delete and reload it.' % release)

    if args.dry_run:
        print('dry run: would load %d rows for release %s%s' % (
            len(keep), release, ' (replacing %d)' % existing if existing else ''))
        return

    db_cols = [DB_NAME.get(c, c) for c in TSV_COLS]
    sql = 'INSERT INTO measurement (%s) VALUES (%s)' % (
        ','.join('`%s`' % c for c in db_cols), ','.join(['%s'] * len(db_cols)))
    try:
        conn.begin()
        if existing:
            cur.execute('DELETE FROM measurement WHERE data_release = %s', (release,))
        for i in range(0, len(keep), BATCH):
            cur.executemany(sql, keep[i:i + BATCH])
            print('  inserted %d / %d' % (min(i + BATCH, len(keep)), len(keep)))
        conn.commit()
    except Exception:
        conn.rollback()
        print('ERROR: rolled back, nothing changed')
        raise
    cur.execute('SELECT status, COUNT(*) FROM measurement WHERE data_release = %s GROUP BY status', (release,))
    print('loaded release %s:' % release, dict(cur.fetchall()))
    conn.close()


if __name__ == '__main__':
    main()
