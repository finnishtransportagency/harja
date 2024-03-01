(ns harja.palvelin.raportointi.vastaanottotarkastus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [clojure.instant :as inst]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :raportointi (component/using
                         (raportointi/luo-raportointi)
                         [:db :pdf-vienti])
          :raportit (component/using
                      (raportit/->Raportit)
                      [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def odotettu-yha-kohteet-2017
  [:taulukko {:otsikko "YHA-kohteet", :tyhja nil, :sheet-nimi "YHA-kohteet", :rivi-ennen [{:sarakkeita 16} {:sarakkeita 1} {:teksti "Hintamuutokset", :sarakkeita 3, :luokka "paallystys-tausta-tumma", :tummenna-teksti? true, :tasaa :keskita} {:sarakkeita 1}], :lisaa-excel-valiotsikot true} [{:otsikko "Urakka", :leveys 10} {:otsikko "Kohde­numero", :leveys 5} {:otsikko "Tunnus", :leveys 5} {:otsikko "Nimi", :leveys 10} {:otsikko "Yö­työ", :leveys 3, :fmt :boolean} {:otsikko "Tie­numero", :leveys 3, :tasaa :oikea} {:otsikko "Aosa", :leveys 3, :tasaa :oikea} {:otsikko "Aet", :leveys 3, :tasaa :oikea} {:otsikko "Losa", :leveys 3, :tasaa :oikea} {:otsikko "Let", :leveys 3, :tasaa :oikea} {:otsikko "Pit. (m)", :leveys 3, :tasaa :oikea} {:otsikko "KVL", :leveys 3, :tasaa :oikea} {:otsikko "YP-lk", :leveys 3} {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha} {:otsikko "Määrä­muu­tokset", :leveys 5, :fmt :raha} {:otsikko "Arvon muu­tok­set", :leveys 5, :fmt :raha} {:otsikko "Sakko­/bonus", :leveys 5, :fmt :raha} {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 10, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}] (list (list "Muhoksen päällystysurakka" "L03" nil "Leppäjärven ramppi" [:boolean {:arvo false}] 20 1 0 3 0 3833 500 8 400M nil 100M 2000M 4543.95M 0M nil 7043.95M) (list "Muhoksen päällystysurakka" "308a" nil "Oulun ohitusramppi" [:boolean {:arvo false}] 20 4 334 10 10 29092 605 8 9000M nil 200M nil 565M 100M nil 9865M) (list "Muhoksen päällystysurakka" "310" nil "Oulaisten ohitusramppi" [:boolean {:arvo false}] 20 19 5 21 15 10137 900 10 500M nil 3457M -3000M 5M 6M nil 968M) (list "Muhoksen päällystysurakka" "666" nil "Kuusamontien testi" [:boolean {:arvo false}] 20 26 1 41 15 79359 66 2 500M nil 3457M nil 5M 6M nil 3968M))])

(def odotettu-paikkauskohteet-2017
  [:taulukko {:otsikko "Muut kohteet", :tyhja nil, :sheet-nimi "Muut kohteet", :rivi-ennen [{:sarakkeita 15} {:sarakkeita 1} {:teksti "Hintamuutokset", :sarakkeita 3, :luokka "paallystys-tausta-tumma", :tummenna-teksti? true, :tasaa :keskita} {:sarakkeita 1}], :lisaa-excel-valiotsikot true} [{:otsikko "Urakka", :leveys 10} {:otsikko "Kohde­numero", :leveys 5} {:otsikko "Tunnus", :leveys 5} {:otsikko "Nimi", :leveys 10} {:otsikko "Yö­työ", :leveys 3, :fmt :boolean} {:otsikko "Tie­numero", :leveys 3, :tasaa :oikea} {:otsikko "Aosa", :leveys 3, :tasaa :oikea} {:otsikko "Aet", :leveys 3, :tasaa :oikea} {:otsikko "Losa", :leveys 3, :tasaa :oikea} {:otsikko "Let", :leveys 3, :tasaa :oikea} {:otsikko "Pit. (m)", :leveys 3, :tasaa :oikea} {:otsikko "KVL", :leveys 3, :tasaa :oikea} {:otsikko "YP-lk", :leveys 3} {:otsikko "Toteutunut hinta", :leveys 10, :fmt :raha} {:otsikko "Arvon muu­tok­set", :leveys 5, :fmt :raha} {:otsikko "Sakko­/bonus", :leveys 5, :fmt :raha} {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 10, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}] (list (list "Muhoksen päällystysurakka" "3456" nil "Ei YHA-kohde" [:boolean {:arvo false}] 20 26 1 41 15 79359 66 2 nil 3457M nil 5M 6M nil 3968M))])

(def odotettu-muut-kustannukset-2017
  [:taulukko {:otsikko "Muut kustannukset", :tyhja "Ei muita kustannuksia.", :sheet-nimi "Muut kustannukset", :lisaa-excel-valiotsikot true} [{:otsikko "Pvm", :leveys 10, :fmt :pvm} {:otsikko "Selitys", :leveys 10} {:otsikko "Summa", :leveys 10, :fmt :raha}] (list [(inst/read-instant-timestamp "2017-01-01T22:06:06.370000000-00:00") "Sakko" -1500M] [(inst/read-instant-timestamp "2017-01-01T22:06:06.370000000-00:00") "Sakko: Oulaisten ohitusramppi (yhaid: 456896958)" -1500M] [(inst/read-instant-timestamp "2017-01-02T22:12:06.370000000-00:00") "Sakko: Oulaisten ohitusramppi (yhaid: 456896958)" -1500M] [(inst/read-instant-timestamp "2017-01-02T22:12:06.370000000-00:00") "Bonus: Leppäjärven ramppi (yhaid: 1233534)" 2000M])])

(def odotettu-yhteenveto-2017
  [:taulukko {:otsikko "Yhteenveto", :tyhja nil, :sheet-nimi "Ylläpitokohteet yhteensä"} (list {:otsikko "", :leveys 5} {:otsikko "", :leveys 5} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "Toteu­tunut hinta (muut kohteet)", :nimi :toteutunut-hinta, :fmt :raha, :leveys 5} {:otsikko "Sakot ja bonukset (muut kuin kohteisiin liittyvät)", :leveys 5, :fmt :raha} {:otsikko "Muut kustannukset", :leveys 5, :fmt :raha} {:otsikko "Arvonväh.", :leveys 5, :fmt :raha} {:otsikko "Sakko/bonus", :leveys 5, :fmt :raha} {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha} {:otsikko "Määrä­muutok­set", :leveys 5, :fmt :raha} {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha} {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}) [(list [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo -2500M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 10671M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo -1000M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 10900M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 5123.95M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 118M, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}] [:arvo-ja-yksikko-korostettu {:arvo 23312.95M, :fmt :raha, :korosta-hennosti? true}])]])

(def odotettu-aikataulu-2017
  [:aikajana {} (list #:harja.ui.aikajana{:otsikko "L03 - Leppäjärven ramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-18T21:00:00.000-00:00", :loppu #inst "2017-05-23T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Koko kohde: 19.05.2017 – 24.05.2017"} #:harja.ui.aikajana{:vari "#282B2A", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-18T21:00:00.000-00:00", :loppu #inst "2017-05-20T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Päällystys: 19.05.2017 – 21.05.2017"} #:harja.ui.aikajana{:vari "#DECB03", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-21T21:00:00.000-00:00", :loppu #inst "2017-05-22T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Tiemerkintä: 22.05.2017 – 23.05.2017"}]} #:harja.ui.aikajana{:otsikko "308a - Oulun ohitusramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-20T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulun ohitusramppi", :teksti "Koko kohde: aloitus 21.05.2017"} #:harja.ui.aikajana{:vari "#282B2A", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-20T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulun ohitusramppi", :teksti "Päällystys: aloitus 21.05.2017"}]} #:harja.ui.aikajana{:otsikko "310 - Oulaisten ohitusramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-25T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulaisten ohitusramppi", :teksti "Koko kohde: aloitus 26.05.2017"}]} #:harja.ui.aikajana{:otsikko "666 - Kuusamontien testi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-06-01T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Kuusamontien testi", :teksti "Koko kohde: aloitus 02.06.2017"}]})])

(def odotettu-kohdeluettelo-2017
  [:taulukko {:otsikko "Kohdeluettelo"} [{:otsikko "Koh­de", :leveys 4, :nimi :kohdenumero, :tyyppi :string} {:otsikko "Nimi", :leveys 8, :nimi :nimi, :tyyppi :string} {:otsikko "Tieosoite", :nimi :tr-osoite, :leveys 8, :tasaa :oikea} {:otsikko "Ajo­radat", :nimi :tr-ajoradat, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "Kais­tat", :nimi :tr-kaistat, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "Pituus", :nimi :pituus, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "YP-lk", :nimi :yllapitoluokka, :leveys 2, :tyyppi :string} {:otsikko "Koh­teen aloi­tus", :leveys 6, :nimi :aikataulu-kohde-alku, :tyyppi :pvm} {:otsikko "Pääl­lystyk­sen aloi­tus", :leveys 6, :nimi :aikataulu-paallystys-alku, :tyyppi :pvm} {:otsikko "Pääl­lystyk­sen lope­tus", :leveys 6, :nimi :aikataulu-paallystys-loppu, :tyyppi :pvm} {:otsikko "Val­mis tie­merkin­tään", :leveys 6, :nimi :valmis-tiemerkintaan} {:otsikko "Tie­merkin­tä val­mis vii­meis­tään", :leveys 6, :nimi :aikataulu-tiemerkinta-takaraja, :tyyppi :pvm} {:otsikko "Tiemer­kinnän aloi­tus", :leveys 6, :nimi :aikataulu-tiemerkinta-alku, :tyyppi :pvm} {:otsikko "Tiemer­kinnän lope­tus", :leveys 6, :nimi :aikataulu-tiemerkinta-loppu, :tyyppi :pvm} {:otsikko "Pääl­lystys­koh­de val­mis", :leveys 6, :nimi :aikataulu-kohde-valmis, :tyyppi :pvm}] [["L03" "Leppäjärven ramppi" "20 / 1 / 0 / 3 / 0" "1" "11" "3833" "1" "19.05.2017" "19.05.2017" "21.05.2017" "21.05.2017" "04.06.2017" "22.05.2017" "23.05.2017" "24.05.2017"] ["308a" "Oulun ohitusramppi" "20 / 4 / 334 / 10 / 10" "0" "11" "29092" "1" "21.05.2017" "21.05.2017" "" "" "" "" "" ""] ["310" "Oulaisten ohitusramppi" "20 / 19 / 5 / 21 / 15" "0" "11" "10137" "3" "26.05.2017" "" "" "" "" "" "" ""] ["666" "Kuusamontien testi" "20 / 26 / 1 / 41 / 15" "0" "11" "79359" "1b" "02.06.2017" "" "" "" "" "" "" ""] ["3456" "Ei YHA-kohde" "20 / 26 / 1 / 41 / 15" "" "" "79359" "1b" "" "" "" "" "" "" "" ""]]])

(def odotettu-yha-kohteet-2023
  (let [odotettu-sopimuksen-mukaiset-tyot 400M
        odotettu-maaramuutokset 1000M
        odotettu-sideaineen-hintamuutokset 5000M
        odotettu-polttooljyn-hintamuutokset 0M
        odotettu-maku-paallysteet 1000M
        odotettu-yhteensa 7400M]

    [:taulukko
     {:otsikko "YHA-kohteet",
      :tyhja nil,
      :sheet-nimi "YHA-kohteet",
      :rivi-ennen
      [{:sarakkeita 14}
       {:sarakkeita 1}
       {:teksti "Hintamuutokset", :sarakkeita 3, :luokka "paallystys-tausta-tumma", :tummenna-teksti? true, :tasaa :keskita}
       {:sarakkeita 1}], :lisaa-excel-valiotsikot true}
     [{:otsikko "Urakka", :leveys 10}
      {:otsikko "Kohde­numero", :leveys 5}
      {:otsikko "Tunnus", :leveys 5}
      {:otsikko "Nimi", :leveys 10}
      {:otsikko "Yö­työ", :leveys 3, :fmt :boolean}
      {:otsikko "Tie­numero", :leveys 3, :tasaa :oikea}
      {:otsikko "Aosa", :leveys 3, :tasaa :oikea}
      {:otsikko "Aet", :leveys 3, :tasaa :oikea}
      {:otsikko "Losa", :leveys 3, :tasaa :oikea}
      {:otsikko "Let", :leveys 3, :tasaa :oikea}
      {:otsikko "Pit. (m)", :leveys 3, :tasaa :oikea}
      {:otsikko "KVL", :leveys 3, :tasaa :oikea}
      {:otsikko "YP-lk", :leveys 3}
      {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha}
      {:otsikko "Määrä­muu­tokset", :leveys 5, :fmt :raha}
      {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 10, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha, :otsikkorivi-luokka "paallystys-tausta-tumma"}
      {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}]
     (list
       (list "Utajärven päällystysurakka" "L15" "A" "Puolangantie" [:boolean {:arvo true}] 837 2 0 2 1000 0 nil nil
         odotettu-sopimuksen-mukaiset-tyot odotettu-maaramuutokset
         odotettu-sideaineen-hintamuutokset odotettu-polttooljyn-hintamuutokset
         odotettu-maku-paallysteet odotettu-yhteensa))]))

(def odotettu-yhteenveto-2023
  (let [odotettu-sopimuksen-mukaiset-tyot 100400M
        odotettu-maaramuutokset 0M
        odotettu-sideaineen-hintamuutokset 4900M
        odotettu-polttooljyn-hintamuutokset -100M
        odotettu-maku-paallysteet 1100M
        odotettu-yhteensa 106300M]
    [:taulukko
     {:otsikko "Yhteenveto",
      :tyhja nil,
      :sheet-nimi "Ylläpitokohteet yhteensä"}
     (list
       {:otsikko "", :leveys 5}
       {:otsikko "", :leveys 5}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "Toteu­tunut hinta (muut kohteet)", :nimi :toteutunut-hinta, :fmt :raha, :leveys 5}
       {:otsikko "Sakot ja bonukset", :leveys 5,  :fmt :raha}
       {:otsikko "Muut kustannukset", :leveys 5, :fmt :raha}
       {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha}
       {:otsikko "Määrä­muutok­set", :leveys 5, :fmt :raha}
       {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha}
       {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha}
       {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha}
       {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha})
     [(list
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0M, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-sopimuksen-mukaiset-tyot, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-maaramuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-sideaineen-hintamuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-polttooljyn-hintamuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-maku-paallysteet, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-yhteensa, :fmt :raha, :korosta-hennosti? true}])]]))

(def odotettu-yhteenveto-utajarvi-2023
  (let [odotettu-sopimuksen-mukaiset-tyot 400M
        odotettu-maaramuutokset 1000M
        odotettu-sideaineen-hintamuutokset 5000M
        odotettu-polttooljyn-hintamuutokset 0M
        odotettu-maku-paallysteet 1000M
        odotettu-yhteensa 7400M]
    [:taulukko
     {:otsikko "Yhteenveto",
      :tyhja nil,
      :sheet-nimi "Ylläpitokohteet yhteensä"}
     (list
       {:otsikko "", :leveys 5}
       {:otsikko "", :leveys 5}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "", :leveys 3}
       {:otsikko "Toteu­tunut hinta (muut kohteet)", :nimi :toteutunut-hinta, :fmt :raha, :leveys 5}
       {:otsikko "Sakot ja bonukset", :leveys 5,  :fmt :raha}
       {:otsikko "Muut kustannukset", :leveys 5, :fmt :raha}
       {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha}
       {:otsikko "Määrä­muutok­set", :leveys 5, :fmt :raha}
       {:otsikko "Bitu­mi-indek­si", :leveys 5, :fmt :raha}
       {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha}
       {:otsikko "MAKU-pääl­lys­teet", :leveys 5, :fmt :raha}
       {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha})
     [(list
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo nil, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo 0, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-sopimuksen-mukaiset-tyot, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-maaramuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-sideaineen-hintamuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-polttooljyn-hintamuutokset, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-maku-paallysteet, :fmt :raha, :korosta-hennosti? true}]
        [:arvo-ja-yksikko-korostettu {:arvo odotettu-yhteensa, :fmt :raha, :korosta-hennosti? true}])]]))

(def odotettu-aikataulu-2023
  [:aikajana
   {}
   (list
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst "2023-06-13T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Puolangantie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 14.06.2023"}
                                #:harja.ui.aikajana{:alku #inst "2023-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Puolangantie"
                                                    :loppu #inst "2023-06-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.06.2023 – 21.06.2023"
                                                    :vari "#282B2A"}]
                         :otsikko "L15 - Puolangantie"
                         :valitavoitteet nil})])

(def odotettu-kohdeluettelo-2023
  [:taulukko
   {:otsikko "Kohdeluettelo"}
   [{:leveys 4
     :nimi :kohdenumero
     :otsikko "Koh­de"
     :tyyppi :string}
    {:leveys 8
     :nimi :nimi
     :otsikko "Nimi"
     :tyyppi :string}
    {:leveys 8
     :nimi :tr-osoite
     :otsikko "Tieosoite"
     :tasaa :oikea}
    {:leveys 2
     :nimi :tr-ajoradat
     :otsikko "Ajo­radat"
     :tasaa :oikea
     :tyyppi :string}
    {:leveys 2
     :nimi :tr-kaistat
     :otsikko "Kais­tat"
     :tasaa :oikea
     :tyyppi :string}
    {:leveys 2
     :nimi :pituus
     :otsikko "Pituus"
     :tasaa :oikea
     :tyyppi :string}
    {:leveys 2
     :nimi :yllapitoluokka
     :otsikko "YP-lk"
     :tyyppi :string}
    {:leveys 6
     :nimi :aikataulu-kohde-alku
     :otsikko "Koh­teen aloi­tus"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :aikataulu-paallystys-alku
     :otsikko "Pääl­lystyk­sen aloi­tus"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :aikataulu-paallystys-loppu
     :otsikko "Pääl­lystyk­sen lope­tus"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :valmis-tiemerkintaan
     :otsikko "Val­mis tie­merkin­tään"}
    {:leveys 6
     :nimi :aikataulu-tiemerkinta-takaraja
     :otsikko "Tie­merkin­tä val­mis vii­meis­tään"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :aikataulu-tiemerkinta-alku
     :otsikko "Tiemer­kinnän aloi­tus"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :aikataulu-tiemerkinta-loppu
     :otsikko "Tiemer­kinnän lope­tus"
     :tyyppi :pvm}
    {:leveys 6
     :nimi :aikataulu-kohde-valmis
     :otsikko "Pääl­lystys­koh­de val­mis"
     :tyyppi :pvm}]
   [["L15"
     "Puolangantie"
     "837 / 2 / 0 / 2 / 1000"
     "0"
     "11"
     "1000"
     "-"
     "14.06.2023"
     "19.06.2023"
     "21.06.2023"
     "03.03.2023"
     ""
     ""
     ""
     ""]]])

(def odotettu-PK-luokka-2023
  [:taulukko {:otsikko "Eurot / PK-luokka", :tyhja nil, :sheet-nimi "Eurot / PK-luokka"} '({:otsikko "Urakka", :leveys 10} {:otsikko "PK1", :leveys 2, :fmt :raha} {:otsikko "PK2", :leveys 2, :fmt :raha} {:otsikko "PK3", :leveys 2, :fmt :raha} {:otsikko "Ei tiedossa", :leveys 2, :fmt :raha}) '({:otsikko "12 Pohjois-Pohjanmaa"} {:lihavoi? false, :rivi [[:arvo {:arvo "7 Utajärven päällystysurakka", :korosta-hennosti? false}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 7400M, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Pohjois-Pohjanmaa yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 7400M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Valtakunnallisesti yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 7400M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]})])

(def odotettu-tyhjat-PKLuokat
  [:taulukko {:otsikko "Eurot / PK-luokka", :tyhja nil, :sheet-nimi "Eurot / PK-luokka"} '({:otsikko "Urakka", :leveys 10} {:otsikko "PK1", :leveys 2, :fmt :raha} {:otsikko "PK2", :leveys 2, :fmt :raha} {:otsikko "PK3", :leveys 2, :fmt :raha} {:otsikko "Ei tiedossa", :leveys 2, :fmt :raha}) '({:otsikko "12 Pohjois-Pohjanmaa"} {:lihavoi? false, :rivi [[:arvo {:arvo "7 Utajärven päällystysurakka", :korosta-hennosti? false}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0M, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Pohjois-Pohjanmaa yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Valtakunnallisesti yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]})])

(def odotettu-taydennetyt-PKLuokat
  [:taulukko {:otsikko "Eurot / PK-luokka", :tyhja nil, :sheet-nimi "Eurot / PK-luokka"} '({:otsikko "Urakka", :leveys 10} {:otsikko "PK1", :leveys 2, :fmt :raha} {:otsikko "PK2", :leveys 2, :fmt :raha} {:otsikko "PK3", :leveys 2, :fmt :raha} {:otsikko "Ei tiedossa", :leveys 2, :fmt :raha}) '({:otsikko "12 Pohjois-Pohjanmaa"} {:lihavoi? false, :rivi [[:arvo {:arvo "7 Utajärven päällystysurakka", :korosta-hennosti? false}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo nil, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 1140M, :jos-tyhja "", :korosta-hennosti? false, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Pohjois-Pohjanmaa yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 1140M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]} {:lihavoi? true, :rivi [[:arvo {:arvo "Valtakunnallisesti yhteensä", :korosta-hennosti? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 0, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}] [:arvo {:arvo 1140M, :jos-tyhja "", :korosta-hennosti? true, :desimaalien-maara 2, :ryhmitelty? true}]]})])

(def odotettu-yha-kohteet-kokomaa-2023
  [:taulukko {:lisaa-excel-valiotsikot true :otsikko "YHA-kohteet" :rivi-ennen [{:sarakkeita 14} {:sarakkeita 1} {:luokka "paallystys-tausta-tumma" :sarakkeita 3 :tasaa :keskita :teksti "Hintamuutokset" :tummenna-teksti? true} {:sarakkeita 1}] :sheet-nimi "YHA-kohteet" :tyhja nil} [{:leveys 10 :otsikko "Urakka"} {:leveys 5 :otsikko "Kohde­numero"} {:leveys 5 :otsikko "Tunnus"} {:leveys 10 :otsikko "Nimi"} {:fmt :boolean :leveys 3 :otsikko "Yö­työ"} {:leveys 3 :otsikko "Tie­numero" :tasaa :oikea} {:leveys 3 :otsikko "Aosa" :tasaa :oikea} {:leveys 3 :otsikko "Aet" :tasaa :oikea} {:leveys 3 :otsikko "Losa" :tasaa :oikea} {:leveys 3 :otsikko "Let" :tasaa :oikea} {:leveys 3 :otsikko "Pit. (m)" :tasaa :oikea} {:leveys 3 :otsikko "KVL" :tasaa :oikea} {:leveys 3 :otsikko "YP-lk"} {:fmt :raha :leveys 5 :otsikko "Tarjous­hinta"} {:fmt :raha :leveys 5 :otsikko "Määrä­muu­tokset"} {:fmt :raha :leveys 5 :otsikko "Bitu­mi-indek­si" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 10 :otsikko "Neste­kaasu ja kevyt poltto­öljy" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 5 :otsikko "MAKU-pääl­lys­teet" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 5 :otsikko "Kokonais­hinta"}] [{:leveys 10 :otsikko "12 Pohjois-Pohjanmaa"} ["Analytiikan testipäällystysurakka" "1" "ANALYTIIKKA1" "Kirkonkylä - Toppinen" [:boolean {:arvo false}] 86 20 0 20 1300 1300 1000 1 100000M -1000M -100M -100M 100M 98900M] ["Utajärven päällystysurakka" "L15" "A" "Puolangantie" [:boolean {:arvo true}] 837 2 0 2 1000 0 nil nil 400M 1000M 5000M 0M 1000M 7400M]]])

(def odotettu-muut-kustannukset-kokomaa-2023
  [:taulukko {:otsikko "Muut kustannukset", :tyhja "Ei muita kustannuksia.", :sheet-nimi "Muut kustannukset", :lisaa-excel-valiotsikot true} [{:otsikko "Pvm", :leveys 10, :fmt :pvm} {:otsikko "Selitys", :leveys 10} {:otsikko "Summa", :leveys 10, :fmt :raha}] []])

(def odotettu-muut-kohteet-kokomaa-2023
  [:taulukko {:lisaa-excel-valiotsikot true :otsikko "Muut kohteet" :rivi-ennen [{:sarakkeita 13} {:sarakkeita 1} {:luokka "paallystys-tausta-tumma" :sarakkeita 3 :tasaa :keskita :teksti "Hintamuutokset" :tummenna-teksti? true} {:sarakkeita 1}] :sheet-nimi "Muut kohteet" :tyhja nil} [{:leveys 10 :otsikko "Urakka"} {:leveys 5 :otsikko "Kohde­numero"} {:leveys 5 :otsikko "Tunnus"} {:leveys 10 :otsikko "Nimi"} {:fmt :boolean :leveys 3 :otsikko "Yö­työ"} {:leveys 3 :otsikko "Tie­numero" :tasaa :oikea} {:leveys 3 :otsikko "Aosa" :tasaa :oikea} {:leveys 3 :otsikko "Aet" :tasaa :oikea} {:leveys 3 :otsikko "Losa" :tasaa :oikea} {:leveys 3 :otsikko "Let" :tasaa :oikea} {:leveys 3 :otsikko "Pit. (m)" :tasaa :oikea} {:leveys 3 :otsikko "KVL" :tasaa :oikea} {:leveys 3 :otsikko "YP-lk"} {:fmt :raha :leveys 10 :otsikko "Toteutunut hinta"} {:fmt :raha :leveys 5 :otsikko "Bitu­mi-indek­si" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 10 :otsikko "Neste­kaasu ja kevyt poltto­öljy" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 5 :otsikko "MAKU-pääl­lys­teet" :otsikkorivi-luokka "paallystys-tausta-tumma"} {:fmt :raha :leveys 5 :otsikko "Kokonais­hinta"}] [{:leveys 10 :otsikko "12 Pohjois-Pohjanmaa"} ["Analytiikan testipäällystysurakka" "2" nil "MT 86 Paavolantie 2" [:boolean {:arvo false}] 86 20 0 20 100 100 nil nil nil nil nil nil 0]]])

(deftest raportin-suoritus-urakalle-toimii-vuosi-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :vastaanottotarkastusraportti
                   :konteksti "urakka"
                   :urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                   :parametrit {:vuosi 2017 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        yha-kohteet (-> vastaus (nth 2))
        muut-kohteet (-> vastaus (nth 3))
        muut-kustannukset (-> vastaus (nth 4))
        yhteenveto (-> vastaus (nth 6))
        aikataulu-otsikko (-> vastaus (nth 5) first)
        aikataulu (-> vastaus (nth 5) second)
        kohdeluettelo (-> vastaus (nth 5) last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Muhoksen päällystysurakka, Vastaanottotarkastus 2017"}))
    (is (= yha-kohteet odotettu-yha-kohteet-2017))
    (is (= muut-kustannukset odotettu-muut-kustannukset-2017))
    (is (= muut-kohteet odotettu-paikkauskohteet-2017))
    (is (= yhteenveto odotettu-yhteenveto-2017))
    (is (= aikataulu-otsikko [:otsikko "Aikataulu"]))
    (is (= aikataulu odotettu-aikataulu-2017))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2017))))

(deftest raportin-suoritus-urakalle-toimii-vuosi-2023
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :vastaanottotarkastusraportti
                   :konteksti "urakka"
                   :urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                   :parametrit {:vuosi 2023 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        yha-kohteet (-> vastaus (nth 2))
        aikataulu-otsikko (-> vastaus (nth 5) first)
        aikataulu (-> vastaus (nth 5) second)
        kohdeluettelo (-> vastaus (nth 5) last)
        yhteenveto (-> vastaus (nth 6))]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Utajärven päällystysurakka, Vastaanottotarkastus 2023"}))
    (is (= yha-kohteet odotettu-yha-kohteet-2023))
    ;; muut kustannukset ja paikkauskohteet -osalta ei ole loogisia sarakemuutoksia vs. 2017, eikä testidatassa mitään sisältöä, joten ei assertoida
    ;; muita kustannuksia ja paikkauskohteita koska se on tehty 2017-testissä
    (is (= yhteenveto odotettu-yhteenveto-utajarvi-2023))
    (is (= aikataulu-otsikko [:otsikko "Aikataulu"]))
    (is (= aikataulu odotettu-aikataulu-2023))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2023))))

(deftest paallystys-yhteenveto-toimii-vuosi-2023
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :vastaanottotarkastusraportti
                   :konteksti "koko maa"
                   :parametrit {:vuosi 2023 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        yha-kohteet (-> vastaus (nth 2))
        muut-kustannukset (-> vastaus (nth 4))
        muut-kohteet (-> vastaus (nth 3))
        yhteenveto (-> vastaus (nth 6))
        pkluokat (-> vastaus (nth 7))]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Päällystysurakoiden yhteenveto, KOKO MAA 2023"}))
    (is (= muut-kohteet odotettu-muut-kohteet-kokomaa-2023))
    (is (= muut-kustannukset odotettu-muut-kustannukset-kokomaa-2023))
    (is (= yha-kohteet odotettu-yha-kohteet-kokomaa-2023))
    (is (= yhteenveto odotettu-yhteenveto-2023))
    (is (= pkluokat odotettu-PK-luokka-2023))))

(deftest generoitu-paallystys-pk-luokka-toimii-vuosi-2023
  (let [;; Poistetaan kaikki ylläpitokohteiden kustannukset ja lisätään hallitusti
        yllapitokohteet (q-map (str "SELECT id, nimi FROM yllapitokohde;"))
        ;; Näytä kustannukset
        kustannukset (map
              #(q-map (format "SELECT yllapitokohde, sopimuksen_mukaiset_tyot, arvonvahennykset, bitumi_indeksi,
             kaasuindeksi,
             toteutunut_hinta,
             maaramuutokset,
             maku_paallysteet
             FROM yllapitokohteen_kustannukset
             WHERE yllapitokohde = %s;" (:id %)))
              yllapitokohteet)
        ;; Siivotaan niiden kustannukset
        _ (mapv
            #(u (format "UPDATE yllapitokohteen_kustannukset
            SET sopimuksen_mukaiset_tyot = 0,
             arvonvahennykset = 0,
             bitumi_indeksi = 0,
             kaasuindeksi = 0,
             toteutunut_hinta = 0,
             maaramuutokset = 0,
             maku_paallysteet = 0 WHERE yllapitokohde = %s;" (:id %)))
            yllapitokohteet)

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :vastaanottotarkastusraportti
                   :konteksti "koko maa"
                   :parametrit {:vuosi 2023 :urakkatyyppi :paallystys}})

        pkluokat (-> vastaus (nth 7))

        _ (is (= pkluokat odotettu-tyhjat-PKLuokat))

        ;; Lisätään kustannukset takaisin hallituilla luvuilla
        _ (mapv
            #(u (format "UPDATE yllapitokohteen_kustannukset
            SET sopimuksen_mukaiset_tyot = 1000,
             bitumi_indeksi = 20,
             kaasuindeksi = 30,
             toteutunut_hinta = 0,
             maaramuutokset = 40,
             maku_paallysteet = 50 WHERE yllapitokohde = %s;" (:id %)))
            yllapitokohteet)

        vastaus2 (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :vastaanottotarkastusraportti
                   :konteksti "koko maa"
                   :parametrit {:vuosi 2023 :urakkatyyppi :paallystys}})
        pkluokat2 (-> vastaus2 (nth 7))
        _ (is (= pkluokat2 odotettu-taydennetyt-PKLuokat))]))

(deftest teiden-pituus-laskenta-sql-toimii
  (let [tie 286
        osa 1
        pituus 1500
        _ (i (format
               "INSERT INTO tr_osien_pituudet 
                        (tie, osa, pituus) 
                        VALUES (%s, %s, %s);" tie osa pituus))
        osa 2
        pituus 2500
        _ (i (format
               "INSERT INTO tr_osien_pituudet 
                                (tie, osa, pituus) 
                                VALUES (%s, %s, %s);" tie osa pituus))
        osa 3
        pituus 3500
        _ (i (format
               "INSERT INTO tr_osien_pituudet 
                                (tie, osa, pituus) 
                                VALUES (%s, %s, %s);" tie osa pituus))

        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, 1, 895, 3, 143);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 3248) "Tien pituuden laskenta toimii tieosat oikeinpäin, monta osaa")

        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, 1, 895, 1, 900);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 5) "Tien pituuden laskenta toimii tieosat oikeinpäin, yksi osa")

        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, 1, 895, 1, 1501);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 0) "Loppuetäisyys menee yli osan pituuden, jolloin palautetaan 0")

        ;; Kun tieosat käännetään, käännetään myös etäisyydet, jolloin aet 144 ja let 895 eikä toisin päin, tulos on 4751
        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, 3, 895, 1, 144);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 4751) "Tien pituuden laskenta toimii tieosat väärinpäin")

        pituus (q-map (str "SELECT * FROM laske_tr_osoitteen_pituus(862424, 1, 142, 1, 895);"))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 0) "Tie numeroa ei ole olemassa")

        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, NULL, 142, 1, 895);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 0) "Tieosaa ei löydy")

        ;; Funktio sallii vielä samalle osalle etäisyyksien antamisen "väärinpäin", ja pituuden laskenta pitäisi onnistua
        ;; aet 900 let 895 -> pituus = 5
        pituus (q-map (format "SELECT * FROM laske_tr_osoitteen_pituus(%s, 1, 900, 1, 895);" tie))
        _ (is (= (-> pituus first :laske_tr_osoitteen_pituus) 5) "Tien pituuden laskenta toimii etäisyydet väärinpäin, sama osa")]))
