#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1
SQL_FORMATOINTI_SCRIPT="${SCRIPT_DIR}/tarkista-sql-formatointi.sh"
FIXTURE_DIR="${SCRIPT_DIR}/sql-formatointi-fixtures"

PASS_COUNT=0
FAIL_COUNT=0

run() {
  OUTPUT=""
  RC=0
  OUTPUT="$(bash "${SQL_FORMATOINTI_SCRIPT}" "$@" 2>&1)" || RC=$?
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "  PASS  $1"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo "  FAIL  $1"
  if [ -n "${2:-}" ]; then
    echo "        ${2}"
  fi
}

echo "SQL-formatointitarkistuksen testit"

run "${FIXTURE_DIR}/good-river.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hyvaksyy river-muotoisen tiedoston"
else
  fail "hyvaksyy river-muotoisen tiedoston" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-select-list-alignment.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "select list"; then
  pass "hylkaa select-listan sisennysvirheen"
else
  fail "hylkaa select-listan sisennysvirheen" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-boundary.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "major clause"; then
  pass "hylkaa boundary-poikkeaman kun river-formatting on kaytossa"
else
  fail "hylkaa boundary-poikkeaman kun river-formatting on kaytossa" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-tabs.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "tab character"; then
  pass "hylkaa tabit"
else
  fail "hylkaa tabit" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/good-comments-and-lowercase.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "ohittaa kommentit ja tukee lowercase-klausuuleja"
else
  fail "ohittaa kommentit ja tukee lowercase-klausuuleja" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-trailing-whitespace.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "trailing whitespace"; then
  pass "hylkaa trailing whitespace -rivit"
else
  fail "hylkaa trailing whitespace -rivit" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/good-major-alignment.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hyvaksyy major clause -start-column alignmentin"
else
  fail "hyvaksyy major clause -start-column alignmentin" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-major-alignment.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "major clause indent mismatch"; then
  pass "hylkaa major clause -start-column poikkeaman"
else
  fail "hylkaa major clause -start-column poikkeaman" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/good-join-alignment.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hyvaksyy join-blokin yhtenaisen sisennyksen"
else
  fail "hyvaksyy join-blokin yhtenaisen sisennyksen" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-join-alignment.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "join clause indent mismatch"; then
  pass "hylkaa join-blokin sisennysvirheen"
else
  fail "hylkaa join-blokin sisennysvirheen" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/good-tail-alignment.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hyvaksyy tail-clause-blokin oman alignmentin"
else
  fail "hyvaksyy tail-clause-blokin oman alignmentin" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/bad-tail-alignment.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "tail clause"; then
  pass "hylkaa tail-clause-poikkeaman"
else
  fail "hylkaa tail-clause-poikkeaman" "rc=${RC}, output=${OUTPUT}"
fi

run "${FIXTURE_DIR}/good-on-conflict-update.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hyvaksyy on-conflict-update-lohkon uutena alignment-jaksona"
else
  fail "hyvaksyy on-conflict-update-lohkon uutena alignment-jaksona" "rc=${RC}, output=${OUTPUT}"
fi

run --mode hygienia "${FIXTURE_DIR}/bad-major-alignment.sql"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hygienia-moodi ohittaa river-alignment-rikkeet"
else
  fail "hygienia-moodi ohittaa river-alignment-rikkeet" "rc=${RC}, output=${OUTPUT}"
fi

run --mode hygienia "${FIXTURE_DIR}/bad-tabs.sql"
if [ ${RC} -ne 0 ] && echo "${OUTPUT}" | grep -q "tab character"; then
  pass "hygienia-moodi hylkaa edelleen tabit"
else
  fail "hygienia-moodi hylkaa edelleen tabit" "rc=${RC}, output=${OUTPUT}"
fi

TMP_FIXTURE="${TMPDIR:-/tmp}/sql-formatointi-fix-$$.sql"
trap 'rm -f "${TMP_FIXTURE}"' EXIT

while IFS= read -r line || [ -n "${line}" ]; do
  printf '%s\n' "${line}"
done < "${FIXTURE_DIR}/bad-tabs.sql" > "${TMP_FIXTURE}"

run --mode hygienia --fix "${TMP_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "Korjattu:"; then
  pass "hygienia-fix korjaa tiedoston paikallaan"
else
  fail "hygienia-fix korjaa tiedoston paikallaan" "rc=${RC}, output=${OUTPUT}"
fi

run --mode hygienia "${TMP_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "hygienia-fix tuottaa tarkistuksen lapaisevan tiedoston"
else
  fail "hygienia-fix tuottaa tarkistuksen lapaisevan tiedoston" "rc=${RC}, output=${OUTPUT}"
fi

if grep -q $'\t' "${TMP_FIXTURE}"; then
  fail "hygienia-fix poistaa tabit tiedostosta"
else
  pass "hygienia-fix poistaa tabit tiedostosta"
fi

if grep -q '[[:space:]]$' "${TMP_FIXTURE}"; then
  fail "hygienia-fix poistaa trailing whitespace -rivit"
else
  pass "hygienia-fix poistaa trailing whitespace -rivit"
fi

TMP_MAJOR_FIXTURE="${TMPDIR:-/tmp}/sql-formatointi-river-major-$$.sql"
TMP_JOIN_FIXTURE="${TMPDIR:-/tmp}/sql-formatointi-river-join-$$.sql"
TMP_TAIL_FIXTURE="${TMPDIR:-/tmp}/sql-formatointi-river-tail-$$.sql"
trap 'rm -f "${TMP_FIXTURE}" "${TMP_MAJOR_FIXTURE}" "${TMP_JOIN_FIXTURE}" "${TMP_TAIL_FIXTURE}"' EXIT

cp "${FIXTURE_DIR}/bad-major-alignment.sql" "${TMP_MAJOR_FIXTURE}"
run --mode river --fix "${TMP_MAJOR_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "Korjattu:"; then
  pass "river-fix korjaa lukitun major-blokin"
else
  fail "river-fix korjaa lukitun major-blokin" "rc=${RC}, output=${OUTPUT}"
fi

run --mode river "${TMP_MAJOR_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "river-fix tuottaa tarkistuksen lapaisevan major-tiedoston"
else
  fail "river-fix tuottaa tarkistuksen lapaisevan major-tiedoston" "rc=${RC}, output=${OUTPUT}"
fi

cat > "${TMP_JOIN_FIXTURE}" <<'EOF'
SELECT id,
       nimi
FROM urakka u
  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id
  LEFT JOIN sopimus s ON u.id = s.urakka
 LEFT JOIN urakka_paatos p ON u.id = p.urakka
WHERE u.poistettu IS NOT TRUE;
EOF
run --mode river --fix "${TMP_JOIN_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "Korjattu:"; then
  pass "river-fix korjaa lukitun join-blokin"
else
  fail "river-fix korjaa lukitun join-blokin" "rc=${RC}, output=${OUTPUT}"
fi

run --mode river "${TMP_JOIN_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "river-fix tuottaa tarkistuksen lapaisevan join-tiedoston"
else
  fail "river-fix tuottaa tarkistuksen lapaisevan join-tiedoston" "rc=${RC}, output=${OUTPUT}"
fi

cp "${FIXTURE_DIR}/bad-tail-alignment.sql" "${TMP_TAIL_FIXTURE}"
run --mode river --fix "${TMP_TAIL_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "Korjattu:"; then
  pass "river-fix korjaa lukitun tail-blokin"
else
  fail "river-fix korjaa lukitun tail-blokin" "rc=${RC}, output=${OUTPUT}"
fi

run --mode river "${TMP_TAIL_FIXTURE}"
if [ ${RC} -eq 0 ] && echo "${OUTPUT}" | grep -q "OK:"; then
  pass "river-fix tuottaa tarkistuksen lapaisevan tail-tiedoston"
else
  fail "river-fix tuottaa tarkistuksen lapaisevan tail-tiedoston" "rc=${RC}, output=${OUTPUT}"
fi

echo ""
echo "Passed: ${PASS_COUNT}"
echo "Failed: ${FAIL_COUNT}"

if [ ${FAIL_COUNT} -ne 0 ]; then
  exit 1
fi