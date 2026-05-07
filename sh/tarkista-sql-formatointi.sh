#!/usr/bin/env bash

set -euo pipefail

MODE="river"
FIX=false

usage() {
  printf 'Käyttö: %s [--mode river|hygienia] [--fix] <sql-tiedosto...>\n' "$0" >&2
}

trimmaa_trailing_whitespace() {
  local line="$1"

  while [[ "$line" == *[[:space:]] ]]; do
    line="${line%[[:space:]]}"
  done

  printf '%s' "$line"
}

korjaa_hygienia_tiedosto() {
  local tiedosto="$1"
  local -a korjatut_rivit=()
  local rivi korjattu_rivi muuttui=false

  while IFS= read -r rivi || [[ -n "$rivi" ]]; do
    korjattu_rivi="${rivi//$'\t'/  }"
    korjattu_rivi="$(trimmaa_trailing_whitespace "$korjattu_rivi")" || exit 1

    if [[ "$korjattu_rivi" != "$rivi" ]]; then
      muuttui=true
    fi

    korjatut_rivit+=("$korjattu_rivi")
  done < "$tiedosto"

  if [[ "$muuttui" == true ]]; then
    printf '%s\n' "${korjatut_rivit[@]}" > "$tiedosto"
    printf 'Korjattu: %s\n' "$tiedosto"
  fi
}

muodosta_sisennetty_rivi() {
  local indent="$1"
  local sisalto="$2"
  local uusi_rivi=""
  local i

  for ((i = 0; i < indent; i++)); do
    uusi_rivi+=" "
  done

  printf '%s%s' "$uusi_rivi" "$sisalto"
}

leading_spaces() {
  local line="$1"
  local trimmed

  trimmed="${line#${line%%[! ]*}}"
  printf '%s' "$(( ${#line} - ${#trimmed} ))"
}

root_keyword_length() {
  local label="$1"
  local root_keyword

  root_keyword="${label%% *}"
  printf '%s' "${#root_keyword}"
}

clause_kind() {
  local line="$1"
  local upper

  upper="$(printf '%s' "$line" | tr '[:lower:]' '[:upper:]')" || exit 1

  case "$upper" in
    GROUP\ BY|GROUP\ BY\ *|ORDER\ BY|ORDER\ BY\ *|HAVING|HAVING\ *|LIMIT|LIMIT\ *|OFFSET|OFFSET\ *)
      printf 'tail'
      return 0
      ;;
    SELECT|SELECT\ *|FROM|FROM\ *|WHERE|WHERE\ *|VALUES|VALUES\ *|UPDATE|UPDATE\ *|SET|SET\ *|INSERT\ INTO|INSERT\ INTO\ *|DELETE\ FROM|DELETE\ FROM\ *|WITH|WITH\ *)
      printf 'major'
      return 0
      ;;
    JOIN|JOIN\ *|LEFT\ JOIN|LEFT\ JOIN\ *|RIGHT\ JOIN|RIGHT\ JOIN\ *|INNER\ JOIN|INNER\ JOIN\ *|FULL\ JOIN|FULL\ JOIN\ *|CROSS\ JOIN|CROSS\ JOIN\ *|LEFT\ OUTER\ JOIN|LEFT\ OUTER\ JOIN\ *|RIGHT\ OUTER\ JOIN|RIGHT\ OUTER\ JOIN\ *|FULL\ OUTER\ JOIN|FULL\ OUTER\ JOIN\ *)
      printf 'join'
      return 0
      ;;
  esac

  return 1
}

clause_label() {
  local line="$1"
  local upper

  upper="$(printf '%s' "$line" | tr '[:lower:]' '[:upper:]')" || exit 1

  case "$upper" in
    LEFT\ OUTER\ JOIN*) printf 'LEFT OUTER JOIN' ;;
    RIGHT\ OUTER\ JOIN*) printf 'RIGHT OUTER JOIN' ;;
    FULL\ OUTER\ JOIN*) printf 'FULL OUTER JOIN' ;;
    GROUP\ BY*) printf 'GROUP BY' ;;
    ORDER\ BY*) printf 'ORDER BY' ;;
    INSERT\ INTO*) printf 'INSERT INTO' ;;
    DELETE\ FROM*) printf 'DELETE FROM' ;;
    LEFT\ JOIN*) printf 'LEFT JOIN' ;;
    RIGHT\ JOIN*) printf 'RIGHT JOIN' ;;
    INNER\ JOIN*) printf 'INNER JOIN' ;;
    FULL\ JOIN*) printf 'FULL JOIN' ;;
    CROSS\ JOIN*) printf 'CROSS JOIN' ;;
    JOIN*) printf 'JOIN' ;;
    SELECT*) printf 'SELECT' ;;
    FROM*) printf 'FROM' ;;
    WHERE*) printf 'WHERE' ;;
    HAVING*) printf 'HAVING' ;;
    LIMIT*) printf 'LIMIT' ;;
    OFFSET*) printf 'OFFSET' ;;
    VALUES*) printf 'VALUES' ;;
    UPDATE*) printf 'UPDATE' ;;
    SET*) printf 'SET' ;;
    WITH*) printf 'WITH' ;;
    *) printf '%s' "$upper" ;;
  esac
}

paivita_syvyys() {
  local line="$1"
  local sanitized="$1"
  local index char depth=0

  sanitized="${sanitized%%--*}"

  for ((index = 0; index < ${#sanitized}; index++)); do
    char="${sanitized:index:1}"
    case "$char" in
      '(') depth=$((depth + 1)) ;;
      ')') depth=$((depth - 1)) ;;
    esac
  done

  printf '%s' "$depth"
}

siivoa_syvemmat_tasot() {
  local sallittu_syvyys="$1"
  local avain

  for avain in "${!major_indent_by_depth[@]}"; do
    if (( avain > sallittu_syvyys )); then
      unset "major_indent_by_depth[$avain]"
      unset "major_boundary_by_depth[$avain]"
      unset "major_mode_by_depth[$avain]"
    fi
  done

  for avain in "${!join_indent_by_depth[@]}"; do
    if (( avain > sallittu_syvyys )); then
      unset "join_indent_by_depth[$avain]"
      unset "join_boundary_by_depth[$avain]"
      unset "join_mode_by_depth[$avain]"
    fi
  done

  for avain in "${!tail_indent_by_depth[@]}"; do
    if (( avain > sallittu_syvyys )); then
      unset "tail_indent_by_depth[$avain]"
      unset "tail_boundary_by_depth[$avain]"
      unset "tail_mode_by_depth[$avain]"
    fi
  done

  for avain in "${!select_list_indent_by_depth[@]}"; do
    if (( avain > sallittu_syvyys )); then
      unset "select_list_indent_by_depth[$avain]"
      unset "select_list_mode_by_depth[$avain]"
    fi
  done
}

resetoi_alignment_tila() {
  major_indent_by_depth=()
  major_boundary_by_depth=()
  major_mode_by_depth=()
  join_indent_by_depth=()
  join_boundary_by_depth=()
  join_mode_by_depth=()
  tail_indent_by_depth=()
  tail_boundary_by_depth=()
  tail_mode_by_depth=()
  select_list_indent_by_depth=()
  select_list_mode_by_depth=()
}

lopeta_select_lista() {
  local syvyys="$1"

  unset "select_list_indent_by_depth[$syvyys]"
  unset "select_list_mode_by_depth[$syvyys]"
}

tarkista_select_list_alignment() {
  local tiedosto="$1"
  local rivinumero="$2"
  local syvyys="$3"
  local indent="$4"
  local rivi_sisalto="$5"
  local tallennettu_indent nykyinen_mode korjattu_rivi

  ALIGNMENT_FIX_APPLIED=false
  ALIGNMENT_FIXED_LINE=""

  tallennettu_indent="${select_list_indent_by_depth[$syvyys]:-}"
  nykyinen_mode="${select_list_mode_by_depth[$syvyys]:-}"

  if [[ "$nykyinen_mode" != "pending" && "$nykyinen_mode" != "locked" ]]; then
    return 0
  fi

  if [[ "$nykyinen_mode" == "pending" ]]; then
    select_list_indent_by_depth[$syvyys]="$indent"
    select_list_mode_by_depth[$syvyys]="locked"
    return 0
  fi

  if (( tallennettu_indent != indent )); then
    if [[ "$FIX" == true ]]; then
      korjattu_rivi="$(muodosta_sisennetty_rivi "$tallennettu_indent" "$rivi_sisalto")" || exit 1
      ALIGNMENT_FIX_APPLIED=true
      ALIGNMENT_FIXED_LINE="$korjattu_rivi"
      return 0
    fi

    printf '%s:%s: select list indent mismatch; expected %s, got %s\n' \
      "$tiedosto" "$rivinumero" "$tallennettu_indent" "$indent"
    return 1
  fi

  return 0
}

tarkista_clause_alignment() {
  local tyyppi="$1"
  local tiedosto="$2"
  local rivinumero="$3"
  local label="$4"
  local syvyys="$5"
  local indent="$6"
  local boundary="$7"
  local rivi_sisalto="$8"
  local tallennettu_indent tallennettu_boundary nykyinen_mode
  local odotettu_indent keyword_length korjattu_rivi

  ALIGNMENT_FIX_APPLIED=false
  ALIGNMENT_FIXED_LINE=""

  if [[ "$tyyppi" == "major" ]]; then
    tallennettu_indent="${major_indent_by_depth[$syvyys]:-}"
    tallennettu_boundary="${major_boundary_by_depth[$syvyys]:-}"
    nykyinen_mode="${major_mode_by_depth[$syvyys]:-}"
  elif [[ "$tyyppi" == "tail" ]]; then
    tallennettu_indent="${tail_indent_by_depth[$syvyys]:-}"
    tallennettu_boundary="${tail_boundary_by_depth[$syvyys]:-}"
    nykyinen_mode="${tail_mode_by_depth[$syvyys]:-}"
  else
    tallennettu_indent="${join_indent_by_depth[$syvyys]:-}"
    tallennettu_boundary="${join_boundary_by_depth[$syvyys]:-}"
    nykyinen_mode="${join_mode_by_depth[$syvyys]:-}"
  fi

  if [[ -z "$nykyinen_mode" ]]; then
    if [[ "$tyyppi" == "major" ]]; then
      major_indent_by_depth[$syvyys]="$indent"
      major_boundary_by_depth[$syvyys]="$boundary"
      major_mode_by_depth[$syvyys]="unknown"
    elif [[ "$tyyppi" == "tail" ]]; then
      tail_indent_by_depth[$syvyys]="$indent"
      tail_boundary_by_depth[$syvyys]="$boundary"
      tail_mode_by_depth[$syvyys]="unknown"
    else
      join_indent_by_depth[$syvyys]="$indent"
      join_boundary_by_depth[$syvyys]="$boundary"
      join_mode_by_depth[$syvyys]="unknown"
    fi
    return 0
  fi

  if [[ "$nykyinen_mode" == "unknown" ]]; then
    if (( tallennettu_indent == indent )); then
      if [[ "$tyyppi" == "major" ]]; then
        major_mode_by_depth[$syvyys]="start"
      elif [[ "$tyyppi" == "tail" ]]; then
        tail_mode_by_depth[$syvyys]="start"
      else
        join_mode_by_depth[$syvyys]="start"
      fi
      return 0
    fi

    if (( tallennettu_boundary == boundary )); then
      if [[ "$tyyppi" == "major" ]]; then
        major_mode_by_depth[$syvyys]="boundary"
      elif [[ "$tyyppi" == "tail" ]]; then
        tail_mode_by_depth[$syvyys]="boundary"
      else
        join_mode_by_depth[$syvyys]="boundary"
      fi
      return 0
    fi

    if [[ "$tyyppi" == "major" ]]; then
      major_indent_by_depth[$syvyys]="$indent"
      major_boundary_by_depth[$syvyys]="$boundary"
      return 0
    fi

    if [[ "$tyyppi" == "tail" ]]; then
      tail_indent_by_depth[$syvyys]="$indent"
      tail_boundary_by_depth[$syvyys]="$boundary"
      return 0
    fi

    printf '%s:%s: %s clause indent mismatch for %s; expected start %s or boundary %s, got start %s and boundary %s\n' \
      "$tiedosto" "$rivinumero" "$tyyppi" "$label" "$tallennettu_indent" "$tallennettu_boundary" "$indent" "$boundary"
    return 1
  fi

  if [[ "$nykyinen_mode" == "start" ]] && (( tallennettu_indent != indent )); then
    if [[ "$FIX" == true ]]; then
      korjattu_rivi="$(muodosta_sisennetty_rivi "$tallennettu_indent" "$rivi_sisalto")" || exit 1
      ALIGNMENT_FIX_APPLIED=true
      ALIGNMENT_FIXED_LINE="$korjattu_rivi"
      return 0
    fi

    printf '%s:%s: %s clause indent mismatch for %s; expected %s, got %s\n' \
      "$tiedosto" "$rivinumero" "$tyyppi" "$label" "$tallennettu_indent" "$indent"
    return 1
  fi

  if [[ "$nykyinen_mode" == "boundary" ]] && (( tallennettu_boundary != boundary )); then
    if [[ "$FIX" == true ]]; then
      keyword_length="$(root_keyword_length "$label")" || exit 1
      odotettu_indent=$((tallennettu_boundary - keyword_length))
      if (( odotettu_indent < 0 )); then
        odotettu_indent=0
      fi
      korjattu_rivi="$(muodosta_sisennetty_rivi "$odotettu_indent" "$rivi_sisalto")" || exit 1
      ALIGNMENT_FIX_APPLIED=true
      ALIGNMENT_FIXED_LINE="$korjattu_rivi"
      return 0
    fi

    printf '%s:%s: %s clause boundary mismatch for %s; expected %s, got %s\n' \
      "$tiedosto" "$rivinumero" "$tyyppi" "$label" "$tallennettu_boundary" "$boundary"
    return 1
  fi

  return 0
}

on_ohitettava_rivi() {
  local line="$1"

  [[ -z "${line//[[:space:]]/}" ]] && return 0
  [[ "$line" =~ ^[[:space:]]*-- ]] && return 0
  return 1
}

on_reset_rivi() {
  local line="$1"
  local trimmed upper

  trimmed="${line#${line%%[! ]*}}"
  upper="$(printf '%s' "$trimmed" | tr '[:lower:]' '[:upper:]')" || exit 1

  [[ "$upper" == ON\ CONFLICT* ]] && return 0
  [[ "$upper" == DO ]] && return 0
  return 1
}

tarkista_tiedosto() {
  local tiedosto="$1"
  local rivinumero=0
  local virheita=0
  local rivi trimmed kind="" label indent boundary keyword_length depth_before depth_delta depth_after
  local -a rivit=()
  local indeksi=0
  local tiedosto_muuttui=false
  resetoi_alignment_tila
  local current_depth=0

  while IFS= read -r rivi || [[ -n "$rivi" ]]; do
    rivit+=("$rivi")
  done < "$tiedosto"

  while (( indeksi < ${#rivit[@]} )); do
    rivi="${rivit[$indeksi]}"
    rivinumero=$((indeksi + 1))
    depth_before="$current_depth"

    if [[ "$rivi" == *$'\t'* ]]; then
      printf '%s:%s: tab character found; käytä välilyöntejä river formatting -asemointiin\n' "$tiedosto" "$rivinumero"
      virheita=1
    fi

    if [[ "$rivi" =~ [[:space:]]$ ]]; then
      printf '%s:%s: trailing whitespace found; poista rivin lopusta ylimääräiset välilyönnit\n' "$tiedosto" "$rivinumero"
      virheita=1
    fi

    if on_ohitettava_rivi "$rivi"; then
      depth_delta="$(paivita_syvyys "$rivi")" || exit 1
      depth_after=$((depth_before + depth_delta))
      if (( depth_after < 0 )); then
        depth_after=0
      fi
      current_depth="$depth_after"
      siivoa_syvemmat_tasot "$current_depth"
      indeksi=$((indeksi + 1))
      continue
    fi

    trimmed="${rivi#${rivi%%[! ]*}}"
    kind=""
    if [[ "$MODE" == "river" ]] && kind="$(clause_kind "$trimmed")"; then
      label="$(clause_label "$trimmed")" || exit 1
      indent="$(leading_spaces "$rivi")" || exit 1
      keyword_length="$(root_keyword_length "$label")" || exit 1
      boundary=$((indent + keyword_length))

      if [[ "$label" == "SELECT" ]]; then
        select_list_mode_by_depth[$depth_before]="pending"
        unset "select_list_indent_by_depth[$depth_before]"
      else
        lopeta_select_lista "$depth_before"
      fi

      if [[ "$kind" == "major" ]]; then
        if ! tarkista_clause_alignment "major" "$tiedosto" "$rivinumero" "$label" "$depth_before" "$indent" "$boundary" "$trimmed"; then
          virheita=1
        elif [[ "$ALIGNMENT_FIX_APPLIED" == true ]]; then
          rivit[$indeksi]="$ALIGNMENT_FIXED_LINE"
          rivi="$ALIGNMENT_FIXED_LINE"
          trimmed="${rivi#${rivi%%[! ]*}}"
          tiedosto_muuttui=true
        fi
      fi

      if [[ "$kind" == "join" ]]; then
        if ! tarkista_clause_alignment "join" "$tiedosto" "$rivinumero" "$label" "$depth_before" "$indent" "$boundary" "$trimmed"; then
          virheita=1
        elif [[ "$ALIGNMENT_FIX_APPLIED" == true ]]; then
          rivit[$indeksi]="$ALIGNMENT_FIXED_LINE"
          rivi="$ALIGNMENT_FIXED_LINE"
          trimmed="${rivi#${rivi%%[! ]*}}"
          tiedosto_muuttui=true
        fi
      fi

      if [[ "$kind" == "tail" ]]; then
        if ! tarkista_clause_alignment "tail" "$tiedosto" "$rivinumero" "$label" "$depth_before" "$indent" "$boundary" "$trimmed"; then
          virheita=1
        elif [[ "$ALIGNMENT_FIX_APPLIED" == true ]]; then
          rivit[$indeksi]="$ALIGNMENT_FIXED_LINE"
          rivi="$ALIGNMENT_FIXED_LINE"
          trimmed="${rivi#${rivi%%[! ]*}}"
          tiedosto_muuttui=true
        fi
      fi
    elif [[ "$MODE" == "river" ]]; then
      if [[ -n "${select_list_mode_by_depth[$depth_before]:-}" ]]; then
        indent="$(leading_spaces "$rivi")" || exit 1
        if ! tarkista_select_list_alignment "$tiedosto" "$rivinumero" "$depth_before" "$indent" "$trimmed"; then
          virheita=1
        elif [[ "$ALIGNMENT_FIX_APPLIED" == true ]]; then
          rivit[$indeksi]="$ALIGNMENT_FIXED_LINE"
          rivi="$ALIGNMENT_FIXED_LINE"
          trimmed="${rivi#${rivi%%[! ]*}}"
          tiedosto_muuttui=true
        fi
      fi
    fi

    depth_delta="$(paivita_syvyys "$rivi")" || exit 1
    depth_after=$((depth_before + depth_delta))
    if (( depth_after < 0 )); then
      depth_after=0
    fi
    current_depth="$depth_after"
    siivoa_syvemmat_tasot "$current_depth"

    if on_reset_rivi "$rivi"; then
      resetoi_alignment_tila
    fi

    if [[ "$rivi" == *';'* ]] && (( current_depth == 0 )); then
      resetoi_alignment_tila
    fi
    indeksi=$((indeksi + 1))
  done

  if [[ "$tiedosto_muuttui" == true ]]; then
    printf '%s\n' "${rivit[@]}" > "$tiedosto"
    printf 'Korjattu: %s\n' "$tiedosto"
  fi

  return "$virheita"
}

main() {
  local tiedosto
  local virheita=0

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --mode)
        if [[ $# -lt 2 ]]; then
          usage
          exit 1
        fi
        MODE="$2"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      --fix)
        FIX=true
        shift
        ;;
      --)
        shift
        break
        ;;
      -*)
        printf 'Tuntematon valitsin: %s\n' "$1" >&2
        usage
        exit 1
        ;;
      *)
        break
        ;;
    esac
  done

  if [[ "$MODE" != "river" && "$MODE" != "hygienia" ]]; then
    printf 'Tuntematon moodi: %s\n' "$MODE" >&2
    usage
    exit 1
  fi

  if [[ $# -eq 0 ]]; then
    usage
    exit 1
  fi

  for tiedosto in "$@"; do
    if [[ ! -f "$tiedosto" ]]; then
      printf '%s: tiedostoa ei löytynyt\n' "$tiedosto" >&2
      virheita=1
      continue
    fi

    if [[ "$FIX" == true ]]; then
      korjaa_hygienia_tiedosto "$tiedosto"
    fi

    if tarkista_tiedosto "$tiedosto"; then
      printf 'OK: %s\n' "$tiedosto"
    else
      virheita=1
    fi
  done

  if [[ "$virheita" -ne 0 ]]; then
    exit 1
  fi
}

main "$@"