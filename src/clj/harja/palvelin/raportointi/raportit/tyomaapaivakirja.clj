(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja
  "Työmaapäiväkirja -näkymän raportti"
  (:require
    [taoensso.timbre :as log]
    [harja.pvm :as pvm]
    [clj-time.coerce :as c]
    [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
    [harja.kyselyt.konversio :as konversio]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.saatiedot :as saatiedot]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahvuus :as vahvuus]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.keliolosuhteet :as keliolosuhteet]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.kalusto :as kalusto]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-toimenpiteet :as muut-toimenpiteet]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.maastotoimeksiannot :as maastotoimeksiannot]
    [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteiset :as yhteiset]))

(defn tapahtumataulukot [tapahtumat otsikko ei-tapahtumia-teksti]
  (let [tapahtumarivit (reduce (fn [rivit onnettomuus]
                                 (conj
                                   rivit
                                   [:jakaja :poista-margin]
                                   (yhteiset/body-teksti (:kuvaus onnettomuus))))
                         [] tapahtumat)]
    
    (into ()
      (conj
        ;; Jos kuvaus on tyhjä, näytetään <ei tietoja>
        (if (empty? (:kuvaus (first tapahtumat)))
          (into []
            [[:jakaja :poista-margin]
             (yhteiset/placeholder-ei-tietoja ei-tapahtumia-teksti)])
          tapahtumarivit)
        [:jakaja :poista-margin]
        (yhteiset/osion-otsikko otsikko)))))

;;FIXME: Tähän alkoi menemään niin paljon aikaa, että tehdessä päädyttiin tekemään yksinkertainen mäppäys
;; aikojen perusteella suorilla tietokantahauilla. Tämä on suorituskyvyn kannalta huono, koska sama pitäisi pystyä tekemään
;; postgressissä yhdellä haulla.
(defn- hae-tehtavat-aikajaksolle [db urakka-id tid versio paivamaara]
  (let [tehtavat1 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db {:urakka-id urakka-id
                                                                         :tyomaapaivakirja_id tid
                                                                         :versio versio
                                                                         :alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 0))
                                                                         :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 2))})
        tehtavat2 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db {:urakka-id urakka-id
                                                                         :tyomaapaivakirja_id tid
                                                                         :versio versio
                                                                         :alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 2))
                                                                         :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))})
        tehtavat3 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db {:urakka-id urakka-id
                                                                         :tyomaapaivakirja_id tid
                                                                         :versio versio
                                                                         :alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 8))
                                                                         :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))})
        tehtavat4 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db {:urakka-id urakka-id
                                                                         :tyomaapaivakirja_id tid
                                                                         :versio versio
                                                                         :alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 14))
                                                                         :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))})
        tehtavat5 (tyomaapaivakirja-kyselyt/hae-paivakirjan-tehtavat db {:urakka-id urakka-id
                                                                         :tyomaapaivakirja_id tid
                                                                         :versio versio
                                                                         :alkuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 22))
                                                                         :loppuaika (c/to-sql-time (pvm/pvm-plus-tuntia paivamaara 24))})]
    [tehtavat1 tehtavat2 tehtavat3 tehtavat4 tehtavat5]))

(defn suorita [db _ {:keys [valittu-rivi]}]
  (if valittu-rivi
    (let [tyomaapaivakirja (first (tyomaapaivakirja-kyselyt/hae-paivakirja db {:tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
                                                                               :versio (:versio valittu-rivi)}))
          ;; Tehtävien tietokantamäppäys on liian monimutkainen, niin haetaan ne erikseen
          tehtavat (hae-tehtavat-aikajaksolle db (:urakka_id valittu-rivi) (:tyomaapaivakirja_id valittu-rivi)
                     (:versio valittu-rivi) (:paivamaara tyomaapaivakirja))
          tehtavat (map
                     (fn [rivi]
                       (map
                         (fn [r]
                           (assoc r :tehtavat (konversio/pgarray->vector (:tehtavat r))))
                         rivi))
                     tehtavat)
          ;; Toimenpiteiden tietokantamäppäys on liian monimutkainen, niin haetaan ne erikseen
          toimenpiteet (tyomaapaivakirja-kyselyt/hae-paivakirjan-toimenpiteet db {:tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
                                                                                  :versio (:versio valittu-rivi)})
          toimenpiteet (map
                         (fn [r]
                           (assoc r :toimenpiteet (konversio/pgarray->vector (:toimenpiteet r))))
                         toimenpiteet)
          tyomaapaivakirja (-> tyomaapaivakirja
                             (update
                               :paivystajat
                               (fn [paivystajat]
                                 (mapv
                                   #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
                                   (konversio/pgarray->vector paivystajat))))
                             (update
                               :tyonjohtajat
                               (fn [tyonjohtajat]
                                 (mapv
                                   #(konversio/pgobject->map % :aloitus :date :lopetus :date :nimi :string)
                                   (konversio/pgarray->vector tyonjohtajat))))
                             (update
                               :saa-asemat
                               (fn [saasemat]
                                 (group-by :aseman_tunniste (mapv
                                                              #(konversio/pgobject->map %
                                                                 :havaintoaika :date :aseman_tunniste :string :aseman_tietojen_paivityshetki :date,
                                                                 :ilman_lampotila :double, :tien_lampotila :double, :keskituuli :long,
                                                                 :sateen_olomuoto :double, :sadesumma :long)
                                                              (konversio/pgarray->vector saasemat)))))
                             (update
                               :poikkeussaat
                               (fn [poikkeussaat]
                                 (mapv
                                   #(konversio/pgobject->map % :havaintoaika :date :paikka :string :kuvaus :string)
                                   (konversio/pgarray->vector poikkeussaat))))
                             (update
                               :kalustot
                               (fn [kalustot]
                                 (mapv
                                   #(konversio/pgobject->map % :aloitus :date :lopetus :date :tyokoneiden_lkm :long :lisakaluston_lkm :long)
                                   (konversio/pgarray->vector kalustot))))
                             ;; Päivitetään kalustolle vielä mahdolliset tehtävät
                             (update
                               :kalustot
                               (fn [kalustot]
                                 (map-indexed
                                   (fn [indeksi kalusto]
                                     (let [;; Pieni indeksitarkistus, ettei käy käpy
                                           kaluston-tehtavat (when (< indeksi (count tehtavat))
                                                               (nth tehtavat indeksi))]
                                       (assoc kalusto :tehtavat (flatten (map :tehtavat kaluston-tehtavat)))))
                                   kalustot)))
                             (update
                               :tapahtumat
                               (fn [tapahtumat]
                                 (mapv
                                   #(konversio/pgobject->map % :tyyppi :string :kuvaus :string)
                                   (konversio/pgarray->vector tapahtumat))))
                             (update
                               :toimeksiannot
                               (fn [toimeksiannot]
                                 (mapv
                                   #(konversio/pgobject->map % :kuvaus :string :aika :double)
                                   (konversio/pgarray->vector toimeksiannot)))))
          onnettomuudet (filter #(= "onnettomuus" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          liikenteenohjaukset (filter #(= "liikenteenohjausmuutos" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          yhteydenotot (filter #(= "tilaajan-yhteydenotto" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          muut-kirjaukset (filter #(= "muut_kirjaukset" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          otsikko "Työmaapäiväkirja"]
      [:raportti {:nimi otsikko
                  :piilota-otsikko? true}

       ; [:tyomaapaivakirja-header tyomaapaivakirja]

       ;; Päivystäjät, Työnjohtajat
       (vahvuus/vahvuus-taulukot (:paivystajat tyomaapaivakirja) (:tyonjohtajat tyomaapaivakirja))
       ;; Sääasemien tiedot
       (saatiedot/saatietojen-taulukot (:saa-asemat tyomaapaivakirja))
       ;; Poikkeukselliset keliolosuhteet
       (keliolosuhteet/poikkeukselliset-keliolosuhteet-taulukko (:poikkeussaat tyomaapaivakirja))
       ;; Kalusto ja tielle tehdyt toimenpiteet
       (kalusto/kalusto-taulukko (:kalustot tyomaapaivakirja))
       ;; Muut toimenpiteet
       (muut-toimenpiteet/muut-toimenpiteet-taulukko toimenpiteet)
       ;; Vahingot ja onnettomuudet
       (tapahtumataulukot onnettomuudet "Vahingot ja onnettomuudet" "Ei vahinkoja tai onnettomuuksia")
       ;; Tilapäiset liikenteenohjaukset
       (tapahtumataulukot liikenteenohjaukset "Tilapäiset liikenteenohjaukset" "Ei liikenteenohjauksia")
       ;; Viranomaispäätöksiin liittyvät maastotoimeksiannot
       (maastotoimeksiannot/maastotoimeksiannot-taulukko (:toimeksiannot tyomaapaivakirja))
       ;; Yhteydenotot ja palautteet, jotka edellyttävät toimenpiteitä
       (tapahtumataulukot yhteydenotot "Yhteydenotot ja palautteet, jotka edellyttävät toimenpiteitä" "Ei yhteydenottoja")
       ;; Muut huomiot
       (tapahtumataulukot muut-kirjaukset "Muut huomiot" "Ei muita huomioita")

       ;; Kommentit (nämäkin pitäisi saada PDF raporttiin)
       ;; Toteutetaan myöhemmin
       #_[:tyomaapaivakirjan-kommentit _]])
    (log/debug "Raportin tiedot eivät ole latautuneet. Odotellaan hetki")))
