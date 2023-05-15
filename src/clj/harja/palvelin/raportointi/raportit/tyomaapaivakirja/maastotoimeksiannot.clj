(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja.maastotoimeksiannot
  "Työmaapäiväkirja -näkymän kalusto ja tien toimenpiteet"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn- maatoimeksianto-rivi [nimi]
  (rivi
    [:varillinen-teksti {:arvo nimi}]))

(defn maastotoimeksiannot-taulukko []
  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Nimi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 12 :tyyppi :varillinen-teksti})
                :rivit (into []
                         [(maatoimeksianto-rivi "3 kpl liittymätarkastelua maantiellä 4121")
                          (maatoimeksianto-rivi "Kaapelitarkastukset Tiekonsultti Oy:n kanssa teillä 4,8,12")
                          (maatoimeksianto-rivi "Siirtoajoneuvo siirto paikasta 4/200/300 kunnan xxx varastoon")])}]
    (into ()
      [(yhteiset/taulukko tiedot)
       (yhteiset/sektio-otsikko "Viranomaispäätöksiin liittyvät maastotoimeksiannot")])))
