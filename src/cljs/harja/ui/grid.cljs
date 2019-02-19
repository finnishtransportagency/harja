(ns harja.ui.grid
  "Gridin ja muokkausgridin koottu rajapinta."
  (:require [harja.ui.grid.protokollat :as protokollat]
            [harja.ui.grid.perus :as perus]
            [harja.ui.grid.muokkaus :as muokkaus]))

(def gridia-muokataan? protokollat/gridia-muokataan?)
(def grid-ohjaus protokollat/grid-ohjaus)
(def otsikko protokollat/otsikko)
(def otsikko? protokollat/otsikko?)
(def aseta-virhe! protokollat/aseta-virhe!)
(def hae-muokkaustila protokollat/hae-muokkaustila)
(def poista-virhe! protokollat/poista-virhe!)
(def nollaa-historia! protokollat/nollaa-historia!)
(def hae-virheet protokollat/hae-virheet)
(def muokkaa-rivit! protokollat/muokkaa-rivit!)
(def validoi-grid protokollat/validoi-grid)
(def aseta-muokkaustila! protokollat/aseta-muokkaustila!)
(def vakiosivutus 250)

(def rivi-piilotetun-otsikon-alla? perus/rivi-piilotetun-otsikon-alla?)
(def otsikko-ja-maara perus/otsikko-ja-maara)

;; UI

(def grid perus/grid)
(def muokkaus-grid muokkaus/muokkaus-grid)
(def arvo-ja-nappi perus/arvo-ja-nappi)
(def rivinvalintasarake perus/rivinvalintasarake)
