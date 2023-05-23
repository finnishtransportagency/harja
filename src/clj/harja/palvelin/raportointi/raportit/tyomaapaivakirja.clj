(ns harja.palvelin.raportointi.raportit.tyomaapaivakirja
  "Työmaapäiväkirja -näkymän raportti"
  (:require
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.saatiedot :as saatiedot]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahvuus :as vahvuus]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.keliolosuhteet :as keliolosuhteet]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.kalusto :as kalusto]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-toimenpiteet :as muut-toimenpiteet]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.vahingot :as vahingot]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.liikenneohjaukset :as liikenneohjaukset]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.maastotoimeksiannot :as maastotoimeksiannot]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.yhteydenotot :as yhteydenotot]
   [harja.palvelin.raportointi.raportit.tyomaapaivakirja.muut-huomiot :as muut-huomiot]))

(defn suorita [_ _ {:keys [valittu-rivi] :as parametrit}]
  (let [otsikko "Työmaapäiväkirja"]

    [:raportti {:nimi otsikko
                :piilota-otsikko? true}

     [:tyomaapaivakirja-header valittu-rivi]

     ;; Päivystäjät, Työnjohtajat
     (vahvuus/vahvuus-taulukot)
     ;; Sääasemien tiedot
     (saatiedot/saatietojen-taulukot)
     ;; Poikkeukselliset keliolosuhteet
     (keliolosuhteet/poikkeukselliset-keliolosuhteet-taulukko)
     ;; Kalusto ja tielle tehdyt toimenpiteet
     (kalusto/kalusto-taulukko)
     ;; Muut toimenpiteet
     (muut-toimenpiteet/muut-toimenpiteet-taulukko)
     ;; Vahingot ja onnettomuudet
     (vahingot/vahingot)
     ;; Tilapäiset liikenteenohjaukset
     (liikenneohjaukset/liikenneohjaukset)
     ;; Viranomaispäätöksiin liittyvät maastotoimeksiannot
     (maastotoimeksiannot/maastotoimeksiannot-taulukko)
     ;; Yhteydenotot ja palautteet, jotka edellyttävät toimenpiteitä
     (yhteydenotot/yhteydenotot-ja-palautteet)
     ;; Muut huomiot
     (muut-huomiot/muut-huomiot)

     ;; Kommentit (nämäkin pitäisi saada PDF raporttiin)
     [:tyomaapaivakirjan-kommentit _]]))
