(ns harja.palvelin.raportointi.aikataulu-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
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

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(def odotettu-aikajana-2023
  [:aikajana
   {}
   (list #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst "2023-06-13T21:00:00.000-00:00"
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

(def odotettu-aikajana-2024
  [:aikajana
   {}
   (list #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst "2024-05-15T21:00:00.000-00:00"
                                                        :drag nil
                                                        :kohde-nimi "Ouluntie 2"
                                                        :loppu #inst "2024-05-23T21:00:00.000-00:00"
                                                        :reuna "black"
                                                        :sahkopostitiedot nil
                                                        :teksti "Koko kohde: 16.05.2024 – 24.05.2024"}
                                    #:harja.ui.aikajana{:alku #inst "2024-05-18T21:00:00.000-00:00"
                                                        :drag nil
                                                        :kohde-nimi "Ouluntie 2"
                                                        :loppu #inst "2024-05-20T21:00:00.000-00:00"
                                                        :sahkopostitiedot nil
                                                        :teksti "Päällystys: 19.05.2024 – 21.05.2024"
                                                        :vari "#282B2A"}
                                    #:harja.ui.aikajana{:alku #inst "2024-05-21T21:00:00.000-00:00"
                                                        :drag nil
                                                        :kohde-nimi "Ouluntie 2"
                                                        :loppu #inst "2024-05-22T21:00:00.000-00:00"
                                                        :sahkopostitiedot nil
                                                        :teksti "Tiemerkintä: 22.05.2024 – 23.05.2024"
                                                        :vari "#DECB03"}]
                             :otsikko "L14 - Ouluntie 2"
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

(def odotettu-kohdeluettelo-2024
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
     "16.05.2024"
     "19.05.2024"
     "21.05.2024"
     "21.05.2024"
     "04.06.2024"
     "22.05.2024"
     "23.05.2024"
     "24.05.2024"]]])

(deftest aikataulu-raportin-suoritus-urakalle-toimii-vuosi-2023
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi :yllapidon-aikataulu
                                 :konteksti "urakka"
                                 :urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                                 :parametrit {:vuosi 2023 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        aikajana (-> vastaus (nth 2))
        kohdeluettelo (-> vastaus last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Utajärven päällystysurakka, Ylläpidon aikataulu 2023" :rajoita-pdf-rivimaara nil}))
    (is (= aikajana odotettu-aikajana-2023))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2023))))

(deftest aikataulu-raportin-suoritus-urakalle-toimii-vuosi-2024
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :yllapidon-aikataulu
                   :konteksti "urakka"
                   :urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                   :parametrit {:vuosi 2024 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        aikajana (-> vastaus (nth 2))
        kohdeluettelo (-> vastaus last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Utajärven päällystysurakka, Ylläpidon aikataulu 2024" :rajoita-pdf-rivimaara nil}))
    (is (= aikajana odotettu-aikajana-2024))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2024))))
