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
(def filteroi-uudet-poistetut protokollat/filteroi-uudet-poistetut)
(def poista-idt protokollat/poista-idt)
(def muokkaa-rivit! protokollat/muokkaa-rivit!)

(def rivi-piilotetun-otsikon-alla? perus/rivi-piilotetun-otsikon-alla?)
(def otsikkorivin-tiedot perus/otsikkorivin-tiedot)

;; UI

(def grid perus/grid)
(def muokkaus-grid muokkaus/muokkaus-grid)
(def erikoismuokattava-kentta perus/arvo-ja-nappi)
