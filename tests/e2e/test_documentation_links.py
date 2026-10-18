"""The Participate page (documentation.do, web/web/homepage/documentation.jsp): every same-site
link opens (HTTP 200, not a not-found page). Several of its PDF links were relative and pointed
at the site root, where the files are not. Links to other sites are not checked. Anonymous, read-only."""
from antweb import BASE_URL, new_session
from test_upload_report_links import _check_all


def test_documentation_page_links():
    html = _check_all(new_session(), BASE_URL + "/documentation.do")
    assert "AntWeb_specimen_data_requirements_2026v1.xlsx" in html
    for gone in ("FMNH_Imaging_Manual_2013.pdf", "data requirements_2015v1.xlsx",
                 "RegionalProject species list instructions", "reginal+species_list_example"):
        assert gone not in html, f"{gone} still linked"
