(ns harja.views.urakka.muutokset.lomake.lomake-muutostyo
  "Muutokset välilehden lomakkeet - Muutostyö"
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot]))


(defn lomake-muutostyo
  [e! {:keys [_valittu-hoitokausi] :as app}]

  [(lomake/ryhma {:otsikko "Perustiedot"}
     (lomake/rivi
       {:otsikko "Kyseessä on"
        :nimi :muutostyo-laji
        :vayla-tyyli? true
        :tyyppi :radio-group
        :vaihtoehto-nayta muutos-domain/+muutostyo-valinnat+
        :vaihtoehdot (keys muutos-domain/+muutostyo-valinnat+)
        :oletusarvo :erillis ;; Toistaiseksi vain erillisrahoitus käytössä
        :vaihtoehto-opts {:poikkeaminen {:disabloitu? true}} ;; Tämä ei käytössä, eikä ole vielä tarkoitus toteuttaa  
        })

     (lomake/rivi
       {:otsikko "Muutostyön nimi"
        :nimi :muutostyo-nimi
        :tyyppi :string
        :pakollinen? true
        :salli-kirjoitus? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :validoi [#(when (nil? (seq %)) "Kirjoita muutostyön nimi")]
        :aputeksti "Anna muutokselle tunnistettava nimi. Nimeä käytetään kulujen kohdistamiseen."
        ::lomake/col-luokka "perustiedot col-sm-6 aputeksti"})

     (yhteiset/+rivi-muutoksen-syy+)
     (yhteiset/+rivi-muutos-voimassa+ app)

     (lomake/rivi
       {:otsikko "Tavoitehinnan muutos"
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :muutostyo-tavoitehinnan-muutos
        :tyyppi :euro
        :teksti-oikealla "EUR"
        :validoi [#(when (nil? %) "Syötä tavoitehinnan muutos")
                  [:rajattu-numero -999999999 999999999 "Anna arvo väliltä 0 - 999 999 999"]]
        ::lomake/col-luokka "perustiedot col-xs-6"})

     (first (yhteiset/liite-kentta e! app)))])
