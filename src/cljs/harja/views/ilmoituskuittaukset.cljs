(ns harja.views.ilmoituskuittaukset
  "Harjan ilmoituskuittausten listaus & uuden kuittauksen kirjaus lomake."
  (:require [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoituskuittaukset :as tiedot]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.bootstrap :as bs]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.ui.viesti :as viesti]))

(defn luo-henkilolomakeryhma [avain nimi]
  (lomake/ryhma {:otsikko    nimi
                 :leveys-col 5}
                {:nimi       (keyword (str avain "-etunimi"))
                 :otsikko    "Etunimi"
                 :leveys-col 3
                 :tyyppi     :string}
                {:nimi       (keyword (str avain "-sukunimi"))
                 :otsikko    "Sukunimi"
                 :leveys-col 3
                 :tyyppi     :string}
                {:nimi       (keyword (str avain "-matkapuhelin"))
                 :otsikko    "Matkapuhelin"
                 :leveys-col 3
                 :tyyppi     :puhelin}
                {:nimi       (keyword (str avain "-tyopuhelin"))
                 :otsikko    "Työpuhelin"
                 :leveys-col 3
                 :tyyppi     :puhelin}
                {:nimi       (keyword (str avain "-sahkoposti"))
                 :otsikko    "Sähköposti"
                 :leveys-col 3
                 :tyyppi     :email}
                {:nimi       (keyword (str avain "-organisaatio"))
                 :otsikko    "Organisaation nimi"
                 :leveys-col 3
                 :tyyppi     :string}
                {:nimi       (keyword (str avain "-ytunnus"))
                 :otsikko    "Organisaation y-tunnus"
                 :leveys-col 3
                 :tyyppi     :string}))

(defn luo-lomake []
  [:span
   [napit/takaisin "Palaa ilmoitukseen" #(reset! tiedot/uusi-kuittaus nil)]
   [lomake/lomake
    {:muokkaa! #(reset! tiedot/uusi-kuittaus %)
     :luokka   :horizontal
     :footer   [napit/palvelinkutsu-nappi
                "Tallenna ja lähetä"
                #(tiedot/tallenna-uusi-kuittaus @tiedot/uusi-kuittaus)
                {:ikoni        (ikonit/tallenna)
                 :disabled     false
                 :kun-onnistuu (fn [vastaus]
                                 (when vastaus
                                   (viesti/nayta! "Kuittaus lähetetty T-LOIK:n." :success)
                                   (ilmoitukset/lisaa-kuittaus-valitulle-ilmoitukselle vastaus))
                                 (reset! tiedot/uusi-kuittaus nil))
                 :virheviesti  "Kuittauksen tallennuksessa tai lähetyksessä T-LOIK:n tapahtui virhe."}]}
    [{:nimi          :tyyppi
      :otsikko       "Tyyppi"
      :pakollinen?   true
      :tyyppi        :valinta
      :valinnat      [:vastaanotto :aloitus :lopetus :muutos :vastaus]
      :valinta-nayta #(case %
                       :vastaanotto "Vastaanotettu"
                       :aloitus "Aloitettu"
                       :lopetus "Lopetettu"
                       :muutos "Muutos"
                       :vastaus "Vastaus"
                       nil)
      :leveys-col    4}
     {:nimi        :vapaateksti
      :otsikko     "Vapaateksti"
      :pakollinen? false
      :tyyppi      :string
      :pituus-max  256
      :leveys-col  4}
     (luo-henkilolomakeryhma "kasittelija" "Käsittelijä")
     (luo-henkilolomakeryhma "ilmoittaja" "Ilmoittaja")]
    @tiedot/uusi-kuittaus]])

(defn uusi-kuittaus-lomake []
  (komp/luo
    (komp/sisaan-ulos
      #(do
        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
        (nav/vaihda-kartan-koko! :hidden))
      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))

    (fn []
      (luo-lomake))))

(defn kuittauksen-tiedot
  [kuittaus]
  (with-meta
    [bs/panel
     {:class "kuittaus-viesti"}
     (capitalize (name (:kuittaustyyppi kuittaus)))
     [:span
      [yleiset/tietoja {}
       "Kuitattu: " (pvm/pvm-aika-sek (:kuitattu kuittaus))
       "Lisätiedot: " (:vapaateksti kuittaus)]
      [:br]
      [yleiset/tietoja {}
       "Kuittaaja: " (apurit/nayta-henkilo (:kuittaaja kuittaus))
       "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kuittaaja kuittaus))
       "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]]]
    {:key (:id kuittaus)}))

