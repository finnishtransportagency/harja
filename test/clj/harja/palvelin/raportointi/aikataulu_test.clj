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

(def odotettu-aikajana-2026
  [:aikajana
   {}
   (list #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2021-05-18T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Mt 18666 Liminka-Tupos"
                                                   :loppu #inst"2021-05-23T21:00:00.000-00:00"
                                                   :reuna "black"
                                                   :sahkopostitiedot nil
                                                   :teksti "Koko kohde: 19.05.2021 – 24.05.2021"}
                               #:harja.ui.aikajana{:alku #inst"2021-05-18T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Mt 18666 Liminka-Tupos"
                                                   :loppu #inst"2021-05-20T21:00:00.000-00:00"
                                                   :sahkopostitiedot nil
                                                   :teksti "Päällystys: 19.05.2021 – 21.05.2021"
                                                   :vari "#282B2A"}
                               #:harja.ui.aikajana{:alku #inst"2021-05-21T21:00:00.000-00:00"
                                                   :drag nil
                                                   :kohde-nimi "Mt 18666 Liminka-Tupos"
                                                   :loppu #inst"2021-05-22T21:00:00.000-00:00"
                                                   :sahkopostitiedot nil
                                                   :teksti "Tiemerkintä: 22.05.2021 – 23.05.2021"
                                                   :vari "#DECB03"}]
                        :otsikko "85 - Mt 18666 Liminka-Tupos"
                        :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2021-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 2"
                                                    :loppu #inst"2021-06-23T21:00:00.000-00:00"
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: 19.06.2021 – 24.06.2021"}
                                #:harja.ui.aikajana{:alku #inst"2021-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 2"
                                                    :loppu #inst"2021-06-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.06.2021 – 21.06.2021"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2021-06-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 2"
                                                    :loppu #inst"2021-06-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.06.2021 – 23.06.2021"
                                                    :vari "#DECB03"}]
                         :otsikko "92 - 70816/805 KLV Hailuodontie 2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2021-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88727/804 KLV Haukipudas"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.06.2021"}
                                #:harja.ui.aikajana{:alku #inst"2021-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88727/804 KLV Haukipudas"
                                                    :loppu nil
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: aloitus 19.06.2021"
                                                    :vari "#282B2A"}]
                         :otsikko "93 - 88727/804 KLV Haukipudas"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/857 KLV Pikkarala-Kosunl"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/857 KLV Pikkarala-Kosunl"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/857 KLV Pikkarala-Kosunl"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "100 - 70022/857 KLV Pikkarala-Kosunl"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88670/850 KLV Vesikarintie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88670/850 KLV Vesikarintie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88670/850 KLV Vesikarintie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "102 - 88670/850 KLV Vesikarintie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 1"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 1"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 1"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "70 - Vt 4 Oulu moottoritie 1"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 2"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 2"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Oulu moottoritie 2"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "71 - Vt 4 Oulu moottoritie 2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Ii"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Ii"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Vt 4 Ii"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "72 - Vt 4 Ii"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Kt 86 Vihanti-Paavola"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Kt 86 Vihanti-Paavola"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Kt 86 Vihanti-Paavola"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "74 - Kt 86 Vihanti-Paavola"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 813 rantakylä-vt 8"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 813 rantakylä-vt 8"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 813 rantakylä-vt 8"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "75 - St 813 rantakylä-vt 8"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 815 Lentokentäntie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 815 Lentokentäntie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 815 Lentokentäntie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "76 - St 815 Lentokentäntie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 816 Hailuodontie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 816 Hailuodontie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 816 Hailuodontie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "77 - St 816 Hailuodontie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 827 Tyrnäväntie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 827 Tyrnäväntie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 827 Tyrnäväntie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "78 - St 827 Tyrnäväntie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 847 Haukiputaantie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 847 Haukiputaantie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 847 Haukiputaantie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "80 - St 847 Haukiputaantie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 848 Kiiminkijoentie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 848 Kiiminkijoentie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "St 848 Kiiminkijoentie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "82 - St 848 Kiiminkijoentie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Yt 8162 Pölläntie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Yt 8162 Pölläntie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Yt 8162 Pölläntie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "84 - Yt 8162 Pölläntie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18709 Alakyläntie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18709 Alakyläntie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18709 Alakyläntie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "87 - Mt 18709 Alakyläntie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18732 Takalontie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18732 Takalontie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Mt 18732 Takalontie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "88 - Mt 18732 Takalontie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/808 KLV Hailuodontie 3"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/808 KLV Hailuodontie 3"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/808 KLV Hailuodontie 3"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "92 - 70816/808 KLV Hailuodontie 3"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70004/836 KLV Ii/vt 4"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70004/836 KLV Ii/vt 4"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70004/836 KLV Ii/vt 4"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "94 - 70004/836 KLV Ii/vt 4"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70851/852 KLV Ii Asematie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70851/852 KLV Ii Asematie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70851/852 KLV Ii Asematie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "94 - 70851/852 KLV Ii Asematie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/870 KLV Ketolanperäntie3"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/870 KLV Ketolanperäntie3"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/870 KLV Ketolanperäntie3"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "95 - 88637/870 KLV Ketolanperäntie3"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/820 KLV Ketolanperäntie1"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/820 KLV Ketolanperäntie1"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/820 KLV Ketolanperäntie1"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "95 - 88637/820 KLV Ketolanperäntie1"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/854 KLV Ketolanperäntie2"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/854 KLV Ketolanperäntie2"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88637/854 KLV Ketolanperäntie2"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "95 - 88637/854 KLV Ketolanperäntie2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/855 KLV Kokkokankaantie2"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/855 KLV Kokkokankaantie2"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/855 KLV Kokkokankaantie2"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "96 - 88681/855 KLV Kokkokankaantie2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/860 KLV Muhos 2"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/860 KLV Muhos 2"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/860 KLV Muhos 2"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "97 - 70022/860 KLV Muhos 2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/810 KLV Muhos 1"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/810 KLV Muhos 1"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70022/810 KLV Muhos 1"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "97 - 70022/810 KLV Muhos 1"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88676/850 KLV Karhuojantie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88676/850 KLV Karhuojantie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88676/850 KLV Karhuojantie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "98 - 88676/850 KLV Karhuojantie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78281/851 KLV Leppiniementie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 19.04.2022"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78281/851 KLV Leppiniementie"
                                                    :loppu #inst"2022-04-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.04.2022 – 21.04.2022"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2022-04-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78281/851 KLV Leppiniementie"
                                                    :loppu #inst"2022-04-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.04.2022 – 23.04.2022"
                                                    :vari "#DECB03"}]
                         :otsikko "99 - 78281/851 KLV Leppiniementie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2023-06-13T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88666/851 KLV Tupoksentie"
                                                    :loppu nil
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: aloitus 14.06.2023"}
                                #:harja.ui.aikajana{:alku #inst"2023-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88666/851 KLV Tupoksentie"
                                                    :loppu #inst"2023-06-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.06.2023 – 21.06.2023"
                                                    :vari "#282B2A"}]
                         :otsikko "101 - 88666/851 KLV Tupoksentie"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2026-05-15T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 1"
                                                    :loppu #inst"2026-05-23T21:00:00.000-00:00"
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: 16.05.2026 – 24.05.2026"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 1"
                                                    :loppu #inst"2026-05-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.05.2026 – 21.05.2026"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "70816/805 KLV Hailuodontie 1"
                                                    :loppu #inst"2026-05-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.05.2026 – 23.05.2026"
                                                    :vari "#DECB03"}]
                         :otsikko "92 - 70816/805 KLV Hailuodontie 1"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2026-05-15T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Ouluntie 2"
                                                    :loppu #inst"2026-05-23T21:00:00.000-00:00"
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: 16.05.2026 – 24.05.2026"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Ouluntie 2"
                                                    :loppu #inst"2026-05-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.05.2026 – 21.05.2026"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "Ouluntie 2"
                                                    :loppu #inst"2026-05-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.05.2026 – 23.05.2026"
                                                    :vari "#DECB03"}]
                         :otsikko "L14 - Ouluntie 2"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2026-05-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78460/855 KLV Haukipudas"
                                                    :loppu #inst"2026-05-23T21:00:00.000-00:00"
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: 19.05.2026 – 24.05.2026"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78460/855 KLV Haukipudas"
                                                    :loppu #inst"2026-05-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.05.2026 – 21.05.2026"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2026-05-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "78460/855 KLV Haukipudas"
                                                    :loppu #inst"2026-05-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.05.2026 – 23.05.2026"
                                                    :vari "#DECB03"}]
                         :otsikko "93 - 78460/855 KLV Haukipudas"
                         :valitavoitteet nil}
     #:harja.ui.aikajana{:ajat [#:harja.ui.aikajana{:alku #inst"2026-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/805 KLV Kokkokankaantie1"
                                                    :loppu #inst"2026-06-23T21:00:00.000-00:00"
                                                    :reuna "black"
                                                    :sahkopostitiedot nil
                                                    :teksti "Koko kohde: 19.06.2026 – 24.06.2026"}
                                #:harja.ui.aikajana{:alku #inst"2026-06-18T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/805 KLV Kokkokankaantie1"
                                                    :loppu #inst"2026-06-20T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Päällystys: 19.06.2026 – 21.06.2026"
                                                    :vari "#282B2A"}
                                #:harja.ui.aikajana{:alku #inst"2026-06-21T21:00:00.000-00:00"
                                                    :drag nil
                                                    :kohde-nimi "88681/805 KLV Kokkokankaantie1"
                                                    :loppu #inst"2026-06-22T21:00:00.000-00:00"
                                                    :sahkopostitiedot nil
                                                    :teksti "Tiemerkintä: 22.06.2026 – 23.06.2026"
                                                    :vari "#DECB03"}]
                         :otsikko "96 - 88681/805 KLV Kokkokankaantie1"
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

(def odotettu-kohdeluettelo-2026
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
   [["85"
     "Mt 18666 Liminka-Tupos"
     "18666 / 1 / 666 / 1 / 7770"
     ""
     ""
     "7104"
     "-"
     "19.05.2021"
     "19.05.2021"
     "21.05.2021"
     "21.05.2021"
     "04.06.2021"
     "22.05.2021"
     "23.05.2021"
     "24.05.2021"]
    ["92"
     "70816/805 KLV Hailuodontie 2"
     "70816 / 805 / 4020 / 805 / 5409"
     ""
     ""
     "1389"
     "-"
     "19.06.2021"
     "19.06.2021"
     "21.06.2021"
     "21.06.2021"
     "04.07.2021"
     "22.06.2021"
     "23.06.2021"
     "24.06.2021"]
    ["93"
     "88727/804 KLV Haukipudas"
     "88727 / 804 / 0 / 804 / 823"
     ""
     ""
     "823"
     "-"
     "19.06.2021"
     "19.06.2021"
     ""
     ""
     ""
     ""
     ""
     ""]
    ["100"
     "70022/857 KLV Pikkarala-Kosunl"
     "70022 / 857 / 0 / 857 / 6300"
     ""
     ""
     "6300"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["102"
     "88670/850 KLV Vesikarintie"
     "88670 / 850 / 5 / 850 / 1055"
     ""
     ""
     "1050"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["70"
     "Vt 4 Oulu moottoritie 1"
     "4 / 364 / 3556 / 403 / 1848"
     ""
     ""
     "17640"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["71"
     "Vt 4 Oulu moottoritie 2"
     "4 / 363 / 7270 / 401 / 2200"
     ""
     ""
     "16915"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["72"
     "Vt 4 Ii"
     "4 / 409 / 2520 / 409 / 6430"
     ""
     ""
     "3910"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["74"
     "Kt 86 Vihanti-Paavola"
     "86 / 20 / 166 / 22 / 5725"
     ""
     ""
     "16422"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["75"
     "St 813 rantakylä-vt 8"
     "813 / 11 / 2360 / 12 / 1098"
     ""
     ""
     ""
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["76"
     "St 815 Lentokentäntie"
     "815 / 2 / 724 / 2 / 4736"
     ""
     ""
     "4012"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["77"
     "St 816 Hailuodontie"
     "816 / 2 / 799 / 2 / 3990"
     ""
     ""
     "3191"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["78"
     "St 827 Tyrnäväntie"
     "827 / 2 / 6303 / 3 / 5452"
     ""
     ""
     ""
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["80"
     "St 847 Haukiputaantie"
     "847 / 4 / 1516 / 4 / 4375"
     ""
     ""
     "2859"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["82"
     "St 848 Kiiminkijoentie"
     "848 / 1 / 9 / 4 / 3580"
     ""
     ""
     ""
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["84"
     "Yt 8162 Pölläntie"
     "8162 / 1 / 0 / 1 / 501"
     ""
     ""
     "501"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["87"
     "Mt 18709 Alakyläntie"
     "18709 / 2 / 540 / 2 / 6457"
     ""
     ""
     "5917"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["88"
     "Mt 18732 Takalontie"
     "18732 / 1 / 1186 / 2 / 4039"
     ""
     ""
     ""
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["92"
     "70816/808 KLV Hailuodontie 3"
     "70816 / 808 / 0 / 808 / 2270"
     ""
     ""
     "2270"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["94"
     "70004/836 KLV Ii/vt 4"
     "70004 / 836 / 1120 / 836 / 2485"
     ""
     ""
     "1365"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["94"
     "70851/852 KLV Ii Asematie"
     "70851 / 852 / 285 / 852 / 646"
     ""
     ""
     "361"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["95"
     "88637/870 KLV Ketolanperäntie3"
     "88637 / 870 / 25 / 870 / 943"
     ""
     ""
     "918"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["95"
     "88637/820 KLV Ketolanperäntie1"
     "88637 / 820 / 0 / 820 / 4103"
     ""
     ""
     "4103"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["95"
     "88637/854 KLV Ketolanperäntie2"
     "88637 / 854 / 0 / 854 / 2770"
     ""
     ""
     "2770"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["96"
     "88681/855 KLV Kokkokankaantie2"
     "88681 / 855 / 0 / 855 / 641"
     ""
     ""
     "641"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["97"
     "70022/860 KLV Muhos 2"
     "70022 / 860 / 620 / 860 / 1760"
     ""
     ""
     "1140"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["97"
     "70022/810 KLV Muhos 1"
     "70022 / 810 / 0 / 810 / 1990"
     ""
     ""
     "1990"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["98"
     "88676/850 KLV Karhuojantie"
     "88676 / 850 / 80 / 850 / 1300"
     ""
     ""
     "1220"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["99"
     "78281/851 KLV Leppiniementie"
     "78281 / 851 / 580 / 851 / 1087"
     ""
     ""
     "507"
     "-"
     "19.04.2022"
     "19.04.2022"
     "21.04.2022"
     ""
     ""
     "22.04.2022"
     "23.04.2022"
     ""]
    ["101"
     "88666/851 KLV Tupoksentie"
     "88666 / 851 / 412 / 851 / 1047"
     ""
     ""
     "635"
     "-"
     "14.06.2023"
     "19.06.2023"
     "21.06.2023"
     "03.03.2023"
     ""
     ""
     ""
     ""]
    ["92"
     "70816/805 KLV Hailuodontie 1"
     "70816 / 805 / 4 / 805 / 2850"
     ""
     ""
     "2846"
     "-"
     "16.05.2026"
     "19.05.2026"
     "21.05.2026"
     "21.05.2026"
     "04.06.2026"
     "22.05.2026"
     "23.05.2026"
     "24.05.2026"]
    ["L14"
     "Ouluntie 2"
     "22 / 13 / 0 / 13 / 3888"
     "1"
     "11"
     ""
     "-"
     "16.05.2026"
     "19.05.2026"
     "21.05.2026"
     "21.05.2026"
     "04.06.2026"
     "22.05.2026"
     "23.05.2026"
     "24.05.2026"]
    ["93"
     "78460/855 KLV Haukipudas"
     "78460 / 855 / 0 / 855 / 400"
     "0"
     "31"
     "400"
     "-"
     "19.05.2026"
     "19.05.2026"
     "21.05.2026"
     "21.05.2026"
     "04.06.2026"
     "22.05.2026"
     "23.05.2026"
     "24.05.2026"]
    ["96"
     "88681/805 KLV Kokkokankaantie1"
     "88681 / 805 / 5 / 805 / 1650"
     "0"
     "31"
     "1645"
     "-"
     "19.06.2026"
     "19.06.2026"
     "21.06.2026"
     "21.06.2026"
     "04.07.2026"
     "22.06.2026"
     "23.06.2026"
     "24.06.2026"]]])

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

(deftest aikataulu-raportin-suoritus-urakalle-toimii-vuosi-2025
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :yllapidon-aikataulu
                   :konteksti "urakka"
                   :urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                   :parametrit {:vuosi 2026 :urakkatyyppi :paallystys}})
        otsikko (-> vastaus (nth 1))
        aikajana (-> vastaus (nth 2))
        kohdeluettelo (-> vastaus last)]
    (is (vector? vastaus))
    (is (= otsikko {:orientaatio :landscape, :nimi "Utajärven päällystysurakka, Ylläpidon aikataulu 2026" :rajoita-pdf-rivimaara nil}))
    (is (= aikajana odotettu-aikajana-2026))
    (is (= kohdeluettelo odotettu-kohdeluettelo-2026))))
