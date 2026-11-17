# Evaluation: main set, rules baseline

Extractor: `rules-baseline`

| Measure | Result |
|---|---|
| Documents | 90 |
| Field accuracy | 94.1% (593 of 630) |
| Documents with every field right | 71.1% (64) |
| Sent to human review | 33.3% |
| Accepted automatically | 60 |
| Accepted automatically but wrong | 0 |
| Model tokens (in / out) | 0 / 0 |
| Estimated cost per document at Bedrock (Claude Haiku 4.5) and Textract list prices | US$0.00150 |

## Accuracy by field

| Field | Accuracy |
|---|---|
| `ACCOUNT_OPENING.account_type` | 100.0% |
| `ACCOUNT_OPENING.address` | 100.0% |
| `ACCOUNT_OPENING.date_of_birth` | 100.0% |
| `ACCOUNT_OPENING.email` | 100.0% |
| `ACCOUNT_OPENING.full_name` | 100.0% |
| `ACCOUNT_OPENING.ic_number` | 100.0% |
| `ACCOUNT_OPENING.initial_deposit` | 100.0% |
| `ACCOUNT_OPENING.phone` | 100.0% |
| `ADDRESS_CHANGE.account_number` | 100.0% |
| `ADDRESS_CHANGE.effective_date` | 80.0% |
| `ADDRESS_CHANGE.full_name` | 100.0% |
| `ADDRESS_CHANGE.ic_number` | 100.0% |
| `ADDRESS_CHANGE.new_address` | 56.7% |
| `ADDRESS_CHANGE.old_address` | 100.0% |
| `LOAN_APPLICATION.employer_name` | 100.0% |
| `LOAN_APPLICATION.full_name` | 100.0% |
| `LOAN_APPLICATION.ic_number` | 100.0% |
| `LOAN_APPLICATION.loan_amount` | 80.0% |
| `LOAN_APPLICATION.loan_type` | 86.7% |
| `LOAN_APPLICATION.monthly_income` | 73.3% |
| `LOAN_APPLICATION.tenure_months` | 100.0% |

## Misses

| Document | Field | Expected | Got | Decision |
|---|---|---|---|---|
| 012-address-change.pdf | new_address | `NO 30 JALAN TERATAI 1 TAMAN DESA JAYA 43000 KAJANG SELANGOR` | _blank_ | HUMAN_REVIEW |
| 015-address-change.pdf | new_address | `NO 48 JALAN KENANGA 3 TAMAN DESA JAYA 75400 MELAKA MELAKA` | _blank_ | HUMAN_REVIEW |
| 023-loan-application.pdf | loan_amount | `180000.00` | _blank_ | HUMAN_REVIEW |
| 026-loan-application.pdf | loan_amount | `125000.00` | _blank_ | HUMAN_REVIEW |
| 026-loan-application.pdf | monthly_income | `9000.00` | _blank_ | HUMAN_REVIEW |
| 033-address-change.pdf | new_address | `NO 81 JALAN KENANGA 1 BANDAR BARU 25200 KUANTAN PAHANG` | _blank_ | HUMAN_REVIEW |
| 033-address-change.pdf | effective_date | `2026-12-08` | _blank_ | HUMAN_REVIEW |
| 036-address-change.pdf | effective_date | `2026-11-05` | _blank_ | HUMAN_REVIEW |
| 041-loan-application.pdf | monthly_income | `28500.00` | _blank_ | HUMAN_REVIEW |
| 044-loan-application.pdf | monthly_income | `28000.00` | _blank_ | HUMAN_REVIEW |
| 045-address-change.pdf | new_address | `NO 13 JALAN CEMPAKA 8 SEKSYEN 7 50450 KUALA LUMPUR WILAYAH PERSEKUTUAN` | _blank_ | HUMAN_REVIEW |
| 050-loan-application.pdf | loan_amount | `140000.00` | _blank_ | HUMAN_REVIEW |
| 050-loan-application.pdf | monthly_income | `19500.00` | _blank_ | HUMAN_REVIEW |
| 051-address-change.pdf | new_address | `NO 88 JALAN TERATAI 9 TAMAN SRI MUDA 40400 SHAH ALAM SELANGOR` | _blank_ | HUMAN_REVIEW |
| 051-address-change.pdf | effective_date | `2027-02-04` | _blank_ | HUMAN_REVIEW |
| 053-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 053-loan-application.pdf | loan_amount | `75000.00` | _blank_ | HUMAN_REVIEW |
| 054-address-change.pdf | new_address | `NO 44 JALAN CEMPAKA 9 TAMAN DESA JAYA 30450 IPOH PERAK` | _blank_ | HUMAN_REVIEW |
| 054-address-change.pdf | effective_date | `2027-01-15` | _blank_ | HUMAN_REVIEW |
| 056-loan-application.pdf | loan_type | `CAR` | _blank_ | HUMAN_REVIEW |
| 057-address-change.pdf | new_address | `NO 33 JALAN MAWAR 6 TAMAN PERMAI 30450 IPOH PERAK` | _blank_ | HUMAN_REVIEW |
| 059-loan-application.pdf | monthly_income | `6000.00` | _blank_ | HUMAN_REVIEW |
| 060-address-change.pdf | new_address | `NO 40 LORONG SERI INDAH 5 BANDAR BARU 30450 IPOH PERAK` | _blank_ | HUMAN_REVIEW |
| 060-address-change.pdf | effective_date | `2027-02-27` | _blank_ | HUMAN_REVIEW |
| 062-loan-application.pdf | loan_type | `CAR` | _blank_ | HUMAN_REVIEW |
| 062-loan-application.pdf | loan_amount | `220000.00` | _blank_ | HUMAN_REVIEW |
| 066-address-change.pdf | new_address | `NO 23 JALAN MAWAR 2 TAMAN SRI MUDA 75400 MELAKA MELAKA` | _blank_ | HUMAN_REVIEW |
| 066-address-change.pdf | effective_date | `2026-11-17` | _blank_ | HUMAN_REVIEW |
| 068-loan-application.pdf | monthly_income | `25500.00` | _blank_ | HUMAN_REVIEW |
| 069-address-change.pdf | new_address | `NO 6 JALAN MAWAR 6 TAMAN PERMAI 15050 KOTA BHARU KELANTAN` | _blank_ | HUMAN_REVIEW |
| 072-address-change.pdf | new_address | `NO 73 JALAN CEMPAKA 3 TAMAN BUKIT INDAH 75400 MELAKA MELAKA` | _blank_ | HUMAN_REVIEW |
| 077-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 077-loan-application.pdf | loan_amount | `10000.00` | _blank_ | HUMAN_REVIEW |
| 077-loan-application.pdf | monthly_income | `13500.00` | _blank_ | HUMAN_REVIEW |
| 078-address-change.pdf | new_address | `NO 1 PERSIARAN BAYU 8 SEKSYEN 7 50450 KUALA LUMPUR WILAYAH PERSEKUTUAN` | _blank_ | HUMAN_REVIEW |
| 083-loan-application.pdf | monthly_income | `9500.00` | _blank_ | HUMAN_REVIEW |
| 087-address-change.pdf | new_address | `NO 60 JALAN DAHLIA 3 SEKSYEN 7 43000 KAJANG SELANGOR` | _blank_ | HUMAN_REVIEW |
