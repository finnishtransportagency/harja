(ns harja.views.urakka.laadunseuranta.bonukset-lomake-test
  (:require [cljs.test :as test :refer-macros [deftest is]]
            [harja.pvm :refer [->pvm]]
            [harja.views.urakka.laadunseuranta.bonukset-lomake :as bonukset-lomake]))

(deftest bonus-konfiguraation-hoitovuosi-seuraa-kasittelyaikaa
  (let [urakan-alkupvm (->pvm "1.10.2021")
        valittu-hoitokausi [(->pvm "1.10.2021") (->pvm "30.9.2022")]
        kasittelyaika (->pvm "15.10.2022")]
    (is (= 2
           (bonukset-lomake/bonus-konfiguraation-hoitovuosi
             urakan-alkupvm
             valittu-hoitokausi
             kasittelyaika))
      "Käsittelyajan pitää ajaa valitun hoitokauden ohi, jotta haku pysyy tallennuksen kanssa samassa kontekstissa")))

(deftest bonus-konfiguraation-hoitovuosi-kayttaa-valittua-hoitokautta-oletuksena
  (let [urakan-alkupvm (->pvm "1.10.2021")
        valittu-hoitokausi [(->pvm "1.10.2021") (->pvm "30.9.2022")]]
    (is (= 1
           (bonukset-lomake/bonus-konfiguraation-hoitovuosi
             urakan-alkupvm
             valittu-hoitokausi
             nil))
      "Ilman käsittelyaikaa haku voi edelleen käyttää valittua hoitokautta oletuksena")))
