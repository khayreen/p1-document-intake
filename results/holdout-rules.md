# Evaluation: holdout set, rules baseline

Extractor: `rules-baseline`

| Measure | Result |
|---|---|
| Documents | 60 |
| Field accuracy | 92.9% (390 of 420) |
| Documents with every field right | 60.0% (36) |
| Sent to human review | 43.3% |
| Accepted automatically | 34 |
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
| `ADDRESS_CHANGE.effective_date` | 65.0% |
| `ADDRESS_CHANGE.full_name` | 100.0% |
| `ADDRESS_CHANGE.ic_number` | 100.0% |
| `ADDRESS_CHANGE.new_address` | 60.0% |
| `ADDRESS_CHANGE.old_address` | 100.0% |
| `LOAN_APPLICATION.employer_name` | 100.0% |
| `LOAN_APPLICATION.full_name` | 100.0% |
| `LOAN_APPLICATION.ic_number` | 100.0% |
| `LOAN_APPLICATION.loan_amount` | 80.0% |
| `LOAN_APPLICATION.loan_type` | 55.0% |
| `LOAN_APPLICATION.monthly_income` | 90.0% |
| `LOAN_APPLICATION.tenure_months` | 100.0% |

## Misses

| Document | Field | Expected | Got | Decision |
|---|---|---|---|---|
| 003-address-change.pdf | effective_date | `2027-01-31` | _blank_ | HUMAN_REVIEW |
| 008-loan-application.pdf | loan_type | `HOUSING` | _blank_ | HUMAN_REVIEW |
| 008-loan-application.pdf | loan_amount | `780000.00` | _blank_ | HUMAN_REVIEW |
| 009-address-change.pdf | effective_date | `2026-12-12` | _blank_ | HUMAN_REVIEW |
| 015-address-change.pdf | new_address | `NO 87 LORONG SERI INDAH 6 TAMAN BUKIT INDAH 81200 JOHOR BAHRU JOHOR` | _blank_ | HUMAN_REVIEW |
| 015-address-change.pdf | effective_date | `2027-02-27` | _blank_ | HUMAN_REVIEW |
| 017-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 018-address-change.pdf | new_address | `NO 66 PERSIARAN BAYU 5 TAMAN SRI MUDA 81200 JOHOR BAHRU JOHOR` | _blank_ | HUMAN_REVIEW |
| 020-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 021-address-change.pdf | new_address | `NO 75 JALAN TERATAI 1 BANDAR BARU 81200 JOHOR BAHRU JOHOR` | _blank_ | HUMAN_REVIEW |
| 023-loan-application.pdf | monthly_income | `28500.00` | _blank_ | HUMAN_REVIEW |
| 030-address-change.pdf | effective_date | `2026-11-20` | _blank_ | HUMAN_REVIEW |
| 032-loan-application.pdf | loan_amount | `340000.00` | _blank_ | HUMAN_REVIEW |
| 035-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 038-loan-application.pdf | loan_type | `HOUSING` | _blank_ | HUMAN_REVIEW |
| 038-loan-application.pdf | monthly_income | `13000.00` | _blank_ | HUMAN_REVIEW |
| 041-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 041-loan-application.pdf | loan_amount | `20000.00` | _blank_ | HUMAN_REVIEW |
| 042-address-change.pdf | new_address | `NO 61 JALAN TERATAI 2 TAMAN DESA JAYA 50450 KUALA LUMPUR WILAYAH PERSEKUTUAN` | _blank_ | HUMAN_REVIEW |
| 042-address-change.pdf | effective_date | `2027-02-27` | _blank_ | HUMAN_REVIEW |
| 044-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 044-loan-application.pdf | loan_amount | `135000.00` | _blank_ | HUMAN_REVIEW |
| 045-address-change.pdf | effective_date | `2027-01-12` | _blank_ | HUMAN_REVIEW |
| 048-address-change.pdf | new_address | `NO 12 JALAN MELATI 6 TAMAN PERMAI 15050 KOTA BHARU KELANTAN` | _blank_ | HUMAN_REVIEW |
| 050-loan-application.pdf | loan_type | `PERSONAL` | _blank_ | HUMAN_REVIEW |
| 051-address-change.pdf | new_address | `NO 56 LORONG SERI INDAH 1 TAMAN BUKIT INDAH 15050 KOTA BHARU KELANTAN` | _blank_ | HUMAN_REVIEW |
| 053-loan-application.pdf | loan_type | `CAR` | _blank_ | HUMAN_REVIEW |
| 054-address-change.pdf | new_address | `NO 26 JALAN DAHLIA 3 TAMAN SRI MUDA 15050 KOTA BHARU KELANTAN` | _blank_ | HUMAN_REVIEW |
| 057-address-change.pdf | new_address | `NO 55 LORONG SERI INDAH 2 TAMAN SRI MUDA 15050 KOTA BHARU KELANTAN` | _blank_ | HUMAN_REVIEW |
| 060-address-change.pdf | effective_date | `2027-01-16` | _blank_ | HUMAN_REVIEW |
