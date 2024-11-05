(ns harja.kyselyt.tyomaapaivakirja
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/tyomaapaivakirja.sql"
  {:positional? false})

(declare
  lisaa-kalusto<! lisaa-paivystaja<! lisaa-tyonjohtaja<! lisaa-saatiedot<! lisaa-poikkeussaa<! lisaa-tie-toimenpide<!
  lisaa-tapahtuma<! lisaa-kommentti<! lisaa-toimeksianto<! lisaa-tyomaapaivakirja<!
  paivita-tyomaapaivakirja<!
  hae-tyomaapaivakirjan-versiotiedot hae-tyomaapaivakirja-viestitunnisteella
  onko-tehtava-olemassa?
  hae-paivakirjalistaus hae-paivakirjan-kommentit hae-paivakirjan-tehtavat hae-poikkeussaa-muutokset
  hae-kalusto-muutokset hae-paivystaja-muutokset hae-saaasema-muutokset hae-tapahtuma-muutokset
  hae-tieston-muutokset hae-toimeksianto-muutokset hae-tyonjohtaja-muutokset
  poista-tyomaapaivakirjan-kommentti<!)
