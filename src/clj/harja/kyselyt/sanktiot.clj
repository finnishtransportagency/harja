(ns harja.kyselyt.sanktiot
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]))

(declare hae-sanktiotyypit hae-sanktiotyypin-tiedot-koodilla hae-sanktio hae-urakan-sanktiot
  hae-urakan-sanktiot-analytiikalle hae-sanktiotyyppi-koodilla merkitse-maksuera-likaiseksi!
  hae-laatupoikkeaman-sanktiot hae-urakan-bonukset hae-sanktion-urakka-id luo-sanktio<!
  paivita-sanktio! hae-suorasanktion-tiedot poista-sanktio!)

;; Käytössä jeesql:ssä
(defn muunna-urakan-sanktio
  "Muuntaa hae-urakan-sanktiot rivien tyypit"
  [rivi]
  (-> rivi
    ;; Muunnetaan sanktiot negatiivisiksi
    (konv/muunna [:summa :indeksikorjaus] -)
    (konv/string->keyword
      :laji
      ;; Muunnetaan ennen kuin ajetaan konv/alaviiva->rakenne sanktioille ja bonuksille
      ;;   -> tämä tehdään "hae-urakan-sanktiot-ja-bonukset" rajapinnassa lopuksi.
      :laatupoikkeama_paatos_kasittelytapa
      :kasittelytapa
      :vakiofraasi)
    (konv/decimal->double :summa :indeksikorjaus)
    ;; Muunna timestampit java-date:ksi
    (konv/muunna [:kasittelyaika :maarattypvm :laatupoikkeama_aika :laatupoikkeama_paatos_kasittelyaika] konv/java-date)
    (update :laatupoikkeama_sijainti #(when % (geo/pg->clj %)))))

;; Käytössä jeesql:ssä
(defn muunna-urakan-bonus
  "Muuntaa hae-urakan-bonukset rivien tyypit"
  [rivi]
  (-> rivi
    (konv/string->keyword :laji :kasittelytapa)
    ;; Muunna timestampit java-date:ksi
    (konv/muunna [:kasittelyaika :maarattypvm] konv/java-date)
    (konv/decimal->double :summa :indeksiåkorjaus)))

;; Käytössä jeesql:ssä
(defn muunna-urakan-lupausbonus
  "Muuntaa hae-urakan-lupausbonukset rivien tyypit"
  [rivi]
  (-> rivi
    (konv/string->keyword :laji)
    (konv/decimal->double :summa)))

(defqueries "harja/kyselyt/sanktiot.sql"
  {:positional? true})
