"""The ants of a specimen's Life Stage field in API records (lifeStageAnts, mixedLifeStage; Brian Fisher 2026-11-18,
after Yuhao Zhao's measurement check found 117 pins whose photos show another ant than the one AntWeb files them under).

The first ant must read as AntWeb reads it (org.calacademy.antweb.Caste.getCasteValues): the expected (caste,
subcaste) pairs below were printed by that Java method on 2026-11-18 (82 examples, including every Life Stage text of
Yuhao's caste sheet, all equal). Run inside the API image (CI) or locally:
  API_DIR=api/v3.1 python3 tests/unit/test_api_life_stage.py
"""
import os
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

# Life Stage text, caste and subcaste of the first ant, as Caste.getCasteValues gives them
JAVA_FIRST_ANT = [
    ('1 alate queen, 1 male', 'queen', 'alate/dealate'),
    ('2 dealate queens, 12 workers', 'queen', 'alate/dealate'),
    ('male and worker', 'male', 'alate'),
    ('queen/male', 'queen', 'alate/dealate'),
    ('1 worker', 'worker', 'normal'),
    ('1w', 'worker', 'normal'),
    ('1 major worker, 1 minor worker', 'worker', 'major/soldier'),
    ('1s, 1w', 'worker', 'major/soldier'),
    ('alate male', 'male', 'alate'),
    ('ergatoid male', 'male', 'ergatoid'),
    ('1 intercaste', 'other', 'intercaste'),
    ('queen pupa', 'queen', 'alate/dealate'),
    ('microgyne and worker', 'queen', 'alate/dealate'),
    ('1 dealate queen', 'queen', 'alate/dealate'),
    ('1 ergatoid queen', 'queen', 'ergatoid'),
    ('2w, 1dq', 'worker', 'normal'),
    ('1 female', None, None),
    ('larva', 'other', 'larva/pupa'),
    ('w & q', 'worker', 'normal'),
    ('new yellow worm', None, None),
    ('3 workers; 1 dealated queen', 'worker', 'normal'),
    ('1 dealate queen, 1 worker, 1 male', 'queen', 'alate/dealate'),
    ('1aq, 1m, 1w', 'queen', 'alate/dealate'),
    ('1 major worker, 1 worker, 1 male', 'worker', 'major/soldier'),
    ('2w, 1aQ', 'worker', 'normal'),
    ('1 major worker, 1 worker', 'worker', 'major/soldier'),
    ('1m, 1dq', 'male', 'alate'),
    ('1m, 1dq, 2w', 'male', 'alate'),
    ('1 major worker, 2 workers', 'worker', 'major/soldier'),
    ('1w major, 1w', 'worker', 'major/soldier'),
    ('1w major, 1w (missing gaster)', 'worker', 'major/soldier'),
    ('2 major workers, 1 worker', 'worker', 'major/soldier'),
    ('1 alate queen, 1 major worker, 1 worker', 'queen', 'alate/dealate'),
    ('1 queen, 1 major worker, 1 worker', 'queen', 'alate/dealate'),
    ('1aQ, 1w', 'queen', 'alate/dealate'),
    ('1 worker, 1 major worker', 'worker', 'normal'),
]


def test_first_ant_reads_as_antweb_reads_it():
    for text, caste, subcaste in JAVA_FIRST_ANT:
        ants = api.life_stage_ants(text)
        first = (ants[0]["caste"], ants[0]["subcaste"]) if ants else (None, None)
        assert first == (caste, subcaste), (text, first, (caste, subcaste))


def test_every_ant_of_the_pin_and_mixed():
    ants = api.life_stage_ants("1 alate queen, 1 male")
    assert ants == [{"caste": "queen", "subcaste": "alate/dealate"}, {"caste": "male", "subcaste": "alate"}]
    assert api.mixed_life_stage(ants)
    assert api.mixed_life_stage(api.life_stage_ants("1 major worker, 1 minor worker"))   # one caste, two subcastes
    assert not api.mixed_life_stage(api.life_stage_ants("3 workers"))
    assert not api.mixed_life_stage(api.life_stage_ants("2 workers, 1 worker"))
    assert api.life_stage_ants(None) == [] and api.life_stage_ants("") == [] and api.life_stage_ants("none") == []
    assert [a["caste"] for a in api.life_stage_ants("w & q / m")] == ["worker", "queen", "male"]


def test_specimen_records_carry_the_fields():
    src = open(api.__file__, encoding="utf-8").read()
    assert "'lifeStageAnts': life_stage_ants(sp.lifeStageSex)" in src
    assert "'mixedLifeStage': mixed_life_stage(" in src


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")
