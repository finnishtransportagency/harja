(ns harja.palvelin.raportointi.vastaanottotarkastus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
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

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn- yhteensa-rivi [kustannuslaji]
  (let [rivi (last (last kustannuslaji))]
    rivi))

(def odotettu-yha-kohteet-2017
  [:taulukko {:otsikko "YHA-kohteet", :tyhja nil, :sheet-nimi "YHA-kohteet"} (list {:otsikko "Kohde­numero", :leveys 5} {:otsikko "Tunnus", :leveys 5} {:otsikko "Nimi", :leveys 10}   {:fmt :boolean :leveys 3 :otsikko "Yö­työ"} {:otsikko "Tie­numero", :leveys 3, :tasaa :oikea} {:otsikko "Aosa", :leveys 3, :tasaa :oikea} {:otsikko "Aet", :leveys 3, :tasaa :oikea} {:otsikko "Losa", :leveys 3, :tasaa :oikea} {:otsikko "Let", :leveys 3, :tasaa :oikea} {:otsikko "Pit. (m)", :leveys 3, :tasaa :oikea} {:otsikko "KVL", :leveys 3, :tasaa :oikea} {:otsikko "YP-lk", :leveys 3} {:otsikko "Tarjous­hinta", :leveys 5, :fmt :raha} {:otsikko "Määrä­muu­tokset", :leveys 5, :fmt :raha} {:otsikko "Arvon muu­tok­set", :leveys 5, :fmt :raha} {:otsikko "Sakko­/bonus", :leveys 5, :fmt :raha} {:otsikko "Side­aineet", :leveys 5, :fmt :raha} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha}   {:fmt :raha :leveys 5 :otsikko "MAKU-päällysteet"} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}) (list (list "L03" nil "Leppäjärven ramppi" [:boolean {:arvo false}] 20 1 0 3 0 3833 500 8 400M 205M 100M 2000M 4543.95M 0M nil 7248.95M) (list "308a" nil "Oulun ohitusramppi" [:boolean {:arvo false}] 20 4 334 10 10 29092 605 8 9000M 0 200M nil 565M 100M nil 9865M) (list "310" nil "Oulaisten ohitusramppi" [:boolean {:arvo false}] 20 19 5 21 15 10137 900 10 500M 0 3457M -3000M 5M 6M nil 968M) (list "666" nil "Kuusamontien testi" [:boolean {:arvo false}] 20 26 1 41 15 79359 66 2 500M 0 3457M nil 5M 6M nil 3968M))])

(def odotettu-paikkauskohteet-2017
  [:taulukko {:otsikko "Muut kohteet", :tyhja nil, :sheet-nimi "Muut kohteet"} (list {:otsikko "Kohde­numero", :leveys 5} {:otsikko "Tunnus", :leveys 5} {:otsikko "Nimi", :leveys 10}   {:fmt :boolean :leveys 3 :otsikko "Yö­työ"} {:otsikko "Tie­numero", :leveys 3, :tasaa :oikea} {:otsikko "Aosa" :leveys 3, :tasaa :oikea} {:otsikko "Aet", :leveys 3, :tasaa :oikea} {:otsikko "Losa", :leveys 3, :tasaa :oikea} {:otsikko "Let", :leveys 3, :tasaa :oikea} {:otsikko "Pit. (m)", :leveys 3, :tasaa :oikea} {:otsikko "KVL", :leveys 3, :tasaa :oikea} {:otsikko "YP-lk", :leveys 3} {:otsikko "Toteutunut hinta", :leveys 10, :fmt :raha} {:otsikko "Arvon muu­tok­set", :leveys 5, :fmt :raha} {:otsikko "Sakko­/bonus", :leveys 5, :fmt :raha} {:otsikko "Side­aineet", :leveys 5, :fmt :raha} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha}   {:fmt :raha :leveys 5 :otsikko "MAKU-päällysteet"} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}) (list (list "3456" nil "Ei YHA-kohde" [:boolean {:arvo false}] 20 26 1 41 15 79359 66 2 nil 3457M nil 5M 6M nil 3968M))])

(def odotettu-muut-kustannukset-2017
  [:taulukko {:otsikko "Muut kustannukset", :tyhja "Ei muita kustannuksia.", :sheet-nimi "Muut kustannukset"} [{:otsikko "Pvm", :leveys 10, :fmt :pvm} {:otsikko "Selitys", :leveys 10} {:otsikko "Summa", :leveys 10, :fmt :raha}] (list [(clojure.instant/read-instant-timestamp "2017-01-01T22:06:06.370000000-00:00") "Sakko" -1500M])])

(def odotettu-yhteenveto-2017
  [:taulukko {:otsikko "Yhteenveto", :tyhja nil, :sheet-nimi "Ylläpitokohteet yhteensä"} (list {:otsikko "", :leveys 5} {:otsikko "", :leveys 5} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:otsikko "", :leveys 3} {:fmt :raha :leveys 5 :nimi :toteutunut-hinta :otsikko "Toteu­tunut hinta (muut kohteet)"} {:fmt :raha :leveys 5 :otsikko "Sakot ja bonukset (muut kuin kohteisiin liittyvät)"} {:otsikko "Muut kustannukset", :leveys 5, :fmt :raha} {:otsikko "Arvonväh.", :leveys 5, :fmt :raha} {:otsikko "Sakko/bonus", :leveys 5, :fmt :raha}   {:fmt :raha :leveys 5 :otsikko "Tarjous­hinta"} {:fmt :raha :leveys 5 :otsikko "Määrä­muutok­set"} {:otsikko "Side­aineet", :leveys 5, :fmt :raha} {:otsikko "Neste­kaasu ja kevyt poltto­öljy", :leveys 5, :fmt :raha}   {:fmt :raha :leveys 5 :otsikko "MAKU-päällysteet"} {:otsikko "Kokonais­hinta", :leveys 5, :fmt :raha}) [(list nil nil nil nil nil nil nil 0 -1500M 0 10671M -1000M 10900M 205M 5123.95M 118M 0 24517.95M)]])

(def odotettu-aikataulu-2017
  [:aikajana {} (list #:harja.ui.aikajana{:otsikko "L03 - Leppäjärven ramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-18T21:00:00.000-00:00", :loppu #inst "2017-05-23T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Koko kohde: 19.05.2017 – 24.05.2017"} #:harja.ui.aikajana{:vari "#282B2A", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-18T21:00:00.000-00:00", :loppu #inst "2017-05-20T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Päällystys: 19.05.2017 – 21.05.2017"} #:harja.ui.aikajana{:vari "#DECB03", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-21T21:00:00.000-00:00", :loppu #inst "2017-05-22T21:00:00.000-00:00", :kohde-nimi "Leppäjärven ramppi", :teksti "Tiemerkintä: 22.05.2017 – 23.05.2017"}]} #:harja.ui.aikajana{:otsikko "308a - Oulun ohitusramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-20T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulun ohitusramppi", :teksti "Koko kohde: aloitus 21.05.2017"} #:harja.ui.aikajana{:vari "#282B2A", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-20T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulun ohitusramppi", :teksti "Päällystys: aloitus 21.05.2017"}]} #:harja.ui.aikajana{:otsikko "310 - Oulaisten ohitusramppi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-05-25T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Oulaisten ohitusramppi", :teksti "Koko kohde: aloitus 26.05.2017"}]} #:harja.ui.aikajana{:otsikko "666 - Kuusamontien testi", :valitavoitteet nil, :ajat [#:harja.ui.aikajana{:reuna "black", :drag nil, :sahkopostitiedot nil, :alku #inst "2017-06-01T21:00:00.000-00:00", :loppu nil, :kohde-nimi "Kuusamontien testi", :teksti "Koko kohde: aloitus 02.06.2017"}]})])

(def odotettu-kohdeluettelo-2017
  [:taulukko {:otsikko "Kohdeluettelo"} [{:otsikko "Koh­de", :leveys 4, :nimi :kohdenumero, :tyyppi :string} {:otsikko "Nimi", :leveys 8, :nimi :nimi, :tyyppi :string} {:otsikko "Tieosoite", :nimi :tr-osoite, :leveys 8, :tasaa :oikea} {:otsikko "Ajo­radat", :nimi :tr-ajoradat, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "Kais­tat", :nimi :tr-kaistat, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "Pituus", :nimi :pituus, :tyyppi :string, :tasaa :oikea, :leveys 2} {:otsikko "YP-lk", :nimi :yllapitoluokka, :leveys 2, :tyyppi :string} {:otsikko "Koh­teen aloi­tus", :leveys 6, :nimi :aikataulu-kohde-alku, :tyyppi :pvm} {:otsikko "Pääl­lystyk­sen aloi­tus", :leveys 6, :nimi :aikataulu-paallystys-alku, :tyyppi :pvm} {:otsikko "Pääl­lystyk­sen lope­tus", :leveys 6, :nimi :aikataulu-paallystys-loppu, :tyyppi :pvm} {:otsikko "Val­mis tie­merkin­tään", :leveys 6, :nimi :valmis-tiemerkintaan} {:otsikko "Tie­merkin­tä val­mis vii­meis­tään", :leveys 6, :nimi :aikataulu-tiemerkinta-takaraja, :tyyppi :pvm} {:otsikko "Tiemer­kinnän aloi­tus", :leveys 6, :nimi :aikataulu-tiemerkinta-alku, :tyyppi :pvm} {:otsikko "Tiemer­kinnän lope­tus", :leveys 6, :nimi :aikataulu-tiemerkinta-loppu, :tyyppi :pvm} {:otsikko "Pääl­lystys­koh­de val­mis", :leveys 6, :nimi :aikataulu-kohde-valmis, :tyyppi :pvm}] [["L03" "Leppäjärven ramppi" "20 / 1 / 0 / 3 / 0" "1" "11" "3833" "1" "19.05.2017" "19.05.2017" "21.05.2017" "21.05.2017" "04.06.2017" "22.05.2017" "23.05.2017" "24.05.2017"] ["308a" "Oulun ohitusramppi" "20 / 4 / 334 / 10 / 10" "0" "11" "29092" "1" "21.05.2017" "21.05.2017" "" "" "" "" "" ""] ["310" "Oulaisten ohitusramppi" "20 / 19 / 5 / 21 / 15" "0" "11" "10137" "3" "26.05.2017" "" "" "" "" "" "" ""] ["666" "Kuusamontien testi" "20 / 26 / 1 / 41 / 15" "0" "11" "79359" "1b" "02.06.2017" "" "" "" "" "" "" ""] ["3456" "Ei YHA-kohde" "20 / 26 / 1 / 41 / 15" "" "" "79359" "1b" "" "" "" "" "" "" "" ""]]])

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

        paikkauskohteet (-> vastaus (nth 4))
        muut-kustannukset (-> vastaus (nth 5))
        yhteenveto (-> vastaus (nth 6))
        aikataulu-otsikko (-> vastaus last first)
        aikataulu (-> vastaus last second)
        kohdeluettelo (-> vastaus last last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Muhoksen päällystysurakka, Vastaanottotarkastus 2017"}))
    (is (= yha-kohteet odotettu-yha-kohteet-2017))
    (is (= muut-kustannukset odotettu-muut-kustannukset-2017))
    (is (= paikkauskohteet odotettu-paikkauskohteet-2017))
    (is (= yhteenveto odotettu-yhteenveto-2017))
    (is (= aikataulu-otsikko [:otsikko "Aikataulu"]))
    (is (= aikataulu odotettu-aikataulu-2017))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2017))))


(def odotettu-yha-kohteet-2023
  (let [odotettu-sopimuksen-mukaiset-tyot 400M
        odotettu-maaramuutokset 1000M
        odotettu-sideaineen-hintamuutokset 5000M
        odotettu-polttooljyn-hintamuutokset 0M
        odotettu-maku-paallysteet 1000M
        odotettu-yhteensa 7400M]
    [:taulukko
     {:otsikko "YHA-kohteet"
      :sheet-nimi "YHA-kohteet"
      :tyhja nil}
     (list {:leveys 5 :otsikko "Kohde­numero"}
           {:leveys 5 :otsikko "Tunnus"}
           {:leveys 10 :otsikko "Nimi"}
           {:fmt :boolean :leveys 3 :otsikko "Yö­työ"}
           {:leveys 3 :otsikko "Tie­numero" :tasaa :oikea}
           {:leveys 3 :otsikko "Aosa" :tasaa :oikea}
           {:leveys 3 :otsikko "Aet" :tasaa :oikea}
           {:leveys 3 :otsikko "Losa" :tasaa :oikea}
           {:leveys 3 :otsikko "Let" :tasaa :oikea}
           {:leveys 3 :otsikko "Pit. (m)" :tasaa :oikea}
           {:leveys 3 :otsikko "KVL" :tasaa :oikea}
           {:leveys 3 :otsikko "YP-lk"}
           {:fmt :raha :leveys 5 :otsikko "Tarjous­hinta"}
           {:fmt :raha :leveys 5 :otsikko "Määrä­muu­tokset"}
           {:fmt :raha :leveys 5 :otsikko "Side­aineet"}
           {:fmt :raha :leveys 5 :otsikko "Neste­kaasu ja kevyt poltto­öljy"}
           {:fmt :raha :leveys 5 :otsikko "MAKU-päällysteet"}
           {:fmt :raha :leveys 5 :otsikko "Kokonais­hinta"})
     (list (list "L14" nil "Ouluntie 2" [:boolean {:arvo nil}] 22 13 0 13 3888 nil nil nil 0M 0M 0M 0M 0M 0M)
           (list "L15" "A" "Puolangantie" [:boolean {:arvo true}] 837 2 0 2 1000 1000 nil nil
                 odotettu-sopimuksen-mukaiset-tyot odotettu-maaramuutokset odotettu-sideaineen-hintamuutokset
                 odotettu-polttooljyn-hintamuutokset odotettu-maku-paallysteet odotettu-yhteensa))]))

(def odotettu-yhteenveto-2023
  (let [odotettu-sopimuksen-mukaiset-tyot 400M
        odotettu-maaramuutokset 1000M
        odotettu-sideaineen-hintamuutokset 5000M
        odotettu-polttooljyn-hintamuutokset 0M
        odotettu-maku-paallysteet 1000M
        odotettu-yhteensa 7400M]
    [:taulukko
    {:otsikko "Yhteenveto"
     :sheet-nimi "Ylläpitokohteet yhteensä"
     :tyhja nil}
    (list {:leveys 5 :otsikko ""}
          {:leveys 5 :otsikko ""}
          {:leveys 3 :otsikko ""}
          {:leveys 3 :otsikko ""}
          {:leveys 3 :otsikko ""}
          {:leveys 3 :otsikko ""}
          {:leveys 3 :otsikko ""}
          {:fmt :raha :leveys 5 :nimi :toteutunut-hinta :otsikko "Toteu­tunut hinta (muut kohteet)"}
          {:fmt :raha :leveys 5 :otsikko "Sakot ja bonukset"}
          {:fmt :raha :leveys 5 :otsikko "Muut kustannukset"}
          {:fmt :raha :leveys 5 :otsikko "Tarjous\u00ADhinta"}
          {:fmt :raha :leveys 5 :otsikko "Määrä\u00ADmuutok\u00ADset"}
          {:fmt :raha :leveys 5 :otsikko "Side­aineet"}
          {:fmt :raha :leveys 5 :otsikko "Neste­kaasu ja kevyt poltto­öljy"}
          {:fmt :raha :leveys 5 :otsikko "MAKU-päällysteet"}
          {:fmt :raha :leveys 5 :otsikko "Kokonais­hinta"})
    [(list nil nil nil nil nil nil nil 0 0 0
           odotettu-sopimuksen-mukaiset-tyot odotettu-maaramuutokset odotettu-sideaineen-hintamuutokset
           odotettu-polttooljyn-hintamuutokset odotettu-maku-paallysteet odotettu-yhteensa)]]))

(def odotettu-aikataulu-2023
  [:aikajana
   {}
   (list #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst "2023-05-15T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Ouluntie 2"
                                                   :loppu #inst "2023-05-23T21:00:00.000-00:00"
                                                   :reuna "black"
                                                   :sahkopostitiedot nil
                                                   :teksti "Koko kohde: 16.05.2023 – 24.05.2023"}
                               #:harja.ui.aikajana{:alku #inst "2023-05-18T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Ouluntie 2"
                                                   :loppu #inst "2023-05-20T21:00:00.000-00:00"
                                                   :sahkopostitiedot nil
                                                   :teksti "Päällystys: 19.05.2023 – 21.05.2023"
                                                   :vari "#282B2A"}
                               #:harja.ui.aikajana{:alku #inst "2023-05-21T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Ouluntie 2"
                                                   :loppu #inst "2023-05-22T21:00:00.000-00:00"
                                                   :sahkopostitiedot nil
                                                   :teksti "Tiemerkintä: 22.05.2023 – 23.05.2023"
                                                   :vari "#DECB03"}]
                        :otsikko "L14 - Ouluntie 2"
                        :valitavoitteet nil}
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
   [["L14"
     "Ouluntie 2"
     "22 / 13 / 0 / 13 / 3888"
     "1"
     "11"
     ""
     "-"
     "16.05.2023"
     "19.05.2023"
     "21.05.2023"
     "21.05.2023"
     "04.06.2023"
     "22.05.2023"
     "23.05.2023"
     "24.05.2023"]
    ["L15"
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
        yhteenveto (-> vastaus (nth 6))
        aikataulu-otsikko (-> vastaus last first)
        aikataulu (-> vastaus last second)
        kohdeluettelo (-> vastaus last last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Utajärven päällystysurakka, Vastaanottotarkastus 2023"}))
    (is (= yha-kohteet odotettu-yha-kohteet-2023))
    ;; muut kustannukset ja paikkauskohteet -osalta ei ole loogisia sarakemuutoksia vs. 2017, eikä testidatassa mitään sisältöä, joten ei assertoida
    ;; muita kustannuksia ja paikkauskohteita koska se on tehty 2017-testissä
    (is (= yhteenveto odotettu-yhteenveto-2023))
    (is (= aikataulu-otsikko [:otsikko "Aikataulu"]))
    (is (= aikataulu odotettu-aikataulu-2023))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2023))))
