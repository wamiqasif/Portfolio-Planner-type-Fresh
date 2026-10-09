CURRENT (confirmed 2026-09-07, via a real captured curl - a JSON body, not query
params; also confirmed against a live call during automation development that
this exact payload is currently returning a bare HTTP 500 with empty body from
the QA backend itself - not a client-side/framework issue, see the Portfolio
Planner business-rule matrix's "Known gaps" section):

curl --location 'https://qaappapi.valueresearch.in/api/v1/planner/generate' \
--header 'Content-Type: application/json' \
--header 'Cookie: <session cookies from a real logged-in browser session>' \
--data '{
    "goal_type": "GROWTH",
    "txn_options": "SIPLUMPSUM",
    "duration_type": "y",
    "investment_duration": 15,
    "monthly_amount": 50000,
    "lumpsum_amount": 10000000,
    "label_id": 89660240,
    "user_id": 123,
    "risk_profile_id": 2,
    "annual_income_range": 2,
    "aware_type": "FRESH",
    "investor_id": 87683240
}'

For PORTFOLIO_AWARE, only "aware_type" changes to "PORTFOLIO_AWARE" - same JSON
body shape otherwise.

STALE (kept for history only - do not use): the two curls below were captured
earlier and send every field as a query string param with no body at all, and
no investor_id_dev. Sending a request this way against the current backend
does not work - PlannerService/PlannerRequest use the JSON-body shape above.

curl --location --request POST 'https://qaappapi.valueresearch.in/api/v1/planner/generate?investor_id_dev=89342299&goal_type=GROWTH&txn_options=SIPLUMPSUM&duration_type=y&investment_duration=15&monthly_amount=50000&lumpsum_amount=10000000&label_id=89660240&user_id=123&risk_profile_id=2&annual_income_range=2&investor_id=87683240' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location --request POST 'https://qaappapi.valueresearch.in/api/v1/planner/generate?investor_id_dev=89342299&goal_type=GROWTH&txn_options=SIPLUMPSUM&duration_type=y&investment_duration=15&monthly_amount=50000&lumpsum_amount=10000000&label_id=89660240&user_id=123&risk_profile_id=2&annual_income_range=2&aware_type=PORTFOLIO_AWARE&investor_id=87683240' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaadvappapi.valueresearch.in/api/v1/user-portfolio/label-opinion-data?label-ids=89660240' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/fund-opinion-data/get-list?plan_id=19701%2C17140%2C40338%2C15752&fields=plan_id%2Cvr_rating%2Copinion_type_id%2Cis_analyst_pick' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/19701' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/fund-opinion-data/get-list?is_analyst_pick=true&fields=plan_id%2Cpriority_order%2Ctag_name' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/fund-plan-options-masters/get-list?fields=plan_id%2Csip_investment_details&plan_id=15752%2C19701%2C40338%2C17140&page=1&limit=3000' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/filter/eNqrVkrLzClJLSpWsoquVirIScyLz0xRsqpWsgUKGJpamFnoGFqaGxjG1tbG1gIAVTQOGg' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'

curl --location 'https://qaappapi.valueresearch.in/api/v1/funds/filter/eNqrVkrLzClJLSpWsoquVipKTY8vyEnMi89MUbKqVrIFChqaWphZ6BhamhsYxtbWxtYCAJ8HD7c' \
--header 'Cookie: aa=81843813%7C423048506%7C863480763; analytics_user_uuid=f3995053-afd4-48ca-9dd4-0defdd96d4ae; arl=574354360; dv=taQUlS1yEoUfITmjd8B7%2BgVIclP5msjnP2Ss8t8bmwqWUzoaDiaMfqvVQgcTQ0NAoRMdwJRaugF%2BgVSC37UFSkhRRTS%2Fta0fhck0amj1JLRW%2FhDBVPwlEN1CJtHBk57AQ9%2Fx8Wh84j1iBjfmrAXhgmGTFDzztGne8qsgjgKuedEdvTw6b85%2BpdAXHb0z827wRUZYR9NTNIF8MVe577TOesEkxidpocI5EdDJLJJItRfxmLRvy8pDvd5DCV2IBgGMYiNs2I5YL36bguIMvD9H7Nk%2F30zZqc65JAOTditQf0oVEzJgxCEReXITjFalf080accvDG%2F1s60RDgy4QUq8qDCVf10qbI6KFmLfW5c1%2FP3SCGGDKvVgDjSunXFzwxxB; jli=1; nobtlgn=311921747'
