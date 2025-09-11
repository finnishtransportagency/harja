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
        :nimi :alityyppi
        :tyyppi :radio-group
        :vayla-tyyli? true
        :pakollinen? true
        :validoi [#(when (nil? %) "Anna alityyppi")]
        :vaihtoehto-nayta muutos-domain/+muutostyo-valinnat+
        :vaihtoehdot (keys muutos-domain/+muutostyo-valinnat+)
        :oletusarvo :erillisrahoitus ;; Toistaiseksi vain erillisrahoitus käytössä
        :vaihtoehto-opts {:poikkeama {:disabloitu? true}} ;; Tämä ei käytössä, eikä ole vielä tarkoitus toteuttaa  
        })

     (lomake/rivi
       {:otsikko "Muutostyön nimi"
        :nimi :nimi
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
     (yhteiset/+rivi-muutos-tavoitehinta+)

     (first (yhteiset/liite-kentta e! app)))])
