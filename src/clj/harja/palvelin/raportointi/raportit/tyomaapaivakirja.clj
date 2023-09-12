(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja
  "Työmaapäiväkirja -näkymän raportti"
  (:require
   [taoensso.timbre :as log]
   [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
   [harja.palvelin.palvelut.tyomaapaivakirja :as tyomaapaivakirja-palvelut]
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

(defn suorita [db _ {:keys [valittu-rivi]}]
  (if (and valittu-rivi (:tila valittu-rivi))
    (let [tyomaapaivakirja (first (tyomaapaivakirja-kyselyt/hae-paivakirja db {:tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
                                                                               :versio (:versio valittu-rivi)}))
          tyomaapaivakirja (tyomaapaivakirja-palvelut/kasittele-tyomaapaivakirjan-tila tyomaapaivakirja)
          ;; Toimenpiteiden tietokantamäppäys on liian monimutkainen, niin haetaan ne erikseen
          toimenpiteet (tyomaapaivakirja-kyselyt/hae-paivakirjan-toimenpiteet db {:tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
                                                                                  :versio (:versio valittu-rivi)})
          toimenpiteet (map
                         (fn [r]
                           (assoc r :toimenpiteet (konversio/pgarray->vector (:toimenpiteet r))))
                         toimenpiteet)
          tyomaapaivakirja (tyomaapaivakirja-palvelut/koverttaa-paivakirjan-data db tyomaapaivakirja (:versio valittu-rivi))
          kommentit (tyomaapaivakirja-kyselyt/hae-paivakirjan-kommentit db {:urakka_id (:urakka_id valittu-rivi)
                                                                            :tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
                                                                            :versio (:versio valittu-rivi)})
          onnettomuudet (filter #(= "onnettomuus" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          liikenteenohjaukset (filter #(= "liikenteenohjausmuutos" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          yhteydenotot (filter #(= "tilaajan-yhteydenotto" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          muut-kirjaukset (filter #(= "muut_kirjaukset" (:tyyppi %)) (:tapahtumat tyomaapaivakirja))
          otsikko "Työmaapäiväkirja"]
      
      [:raportti {:nimi otsikko
                  :piilota-otsikko? true}

       [:tyomaapaivakirja-header tyomaapaivakirja]

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

       ;; Kommentit
       [:tyomaapaivakirjan-kommentit kommentit]])
    (log/debug "Raportin tiedot eivät ole latautuneet. Odotellaan hetki")))
