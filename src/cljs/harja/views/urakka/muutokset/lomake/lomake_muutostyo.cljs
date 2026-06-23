(ns harja.views.urakka.muutokset.lomake.lomake-muutostyo
  "Muutokset välilehden lomakkeet - Muutostyö"
  (:require [harja.ui.lomake :as lomake]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]))


(defn lomake-muutostyo
  [e! {:keys [valittu-hoitokausi urakan-hoitokaudet] :as app}]

  [(lomake/ryhma {:otsikko "Perustiedot"}
     ;; TODO: Muutostyön alityypin valinta on toistaiseksi poistettu käytöstä, koska vain 'erillisrahoitus' on sallittu vaihtoehto.
     ;;       Erillisrahoitus-alityyppi asetetaan oletuksena muutoslomake.cljs 'alusta-lomakkeen-pohjatiedot'-funktiossa.
     ;;       Kun alityypin valinta otetaan takaisin käyttöön, poista oletusarvon asetus pohjatietojen alustuksesta.
     #_(lomake/rivi
       {:otsikko "Kyseessä on"
        :nimi :alityyppi
        :tyyppi :radio-group
        :vayla-tyyli? true
        :pakollinen? true
        :validoi [#(when (nil? %) "Anna alityyppi")]
        :vaihtoehto-nayta muutos-domain/+muutostyo-valinnat+
        :vaihtoehdot (keys muutos-domain/+muutostyo-valinnat+)
        :oletusarvo :erillisrahoitus ;; Toistaiseksi vain erillisrahoitus käytössä
        :vaihtoehto-opts {:poikkeama {:disabloitu? true}} ;; TODO - tarvitaan poikkeama
        })

     (lomake/rivi
       {:otsikko "Muutostyön nimi"
        :nimi :nimi
        :tyyppi :string
        :pakollinen? true
        :salli-kirjoitus? true
        :piilota-checkbox? true
        :piilota-dropdown? true
        :validoi [#(when (nil? (seq %)) "Syötä muutostyön nimi")]
        :aputeksti "Anna muutokselle tunnistettava nimi. Nimeä käytetään kulujen kohdistamiseen."
        ::lomake/col-luokka "perustiedot col-sm-6 aputeksti"})

     (yhteiset/+rivi-muutoksen-syy+)
     (yhteiset/+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi)
     (yhteiset/+rivi-muutos-tavoitehinta+)

     (first (yhteiset/liite-kentta e! app)))])
