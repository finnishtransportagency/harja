(ns harja.views.ilmoituskuittaukset
  "Harjan ilmoituskuittausten listaus & uuden kuittauksen kirjaus lomake."
  (:require [clojure.string :refer [capitalize]]
            [reagent.core :refer [atom]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoituskuittaukset :as tiedot]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.bootstrap :as bs]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.ui.viesti :as viesti]))

(defonce kasittelija-auki? (atom false))

(defn kasittele-kuittauskasityksen-vastaus [vastaus]
  (when vastaus
    (viesti/nayta! "Kuittaus lähetetty T-LOIK:n." :success)
    (ilmoitukset/lisaa-kuittaus-valitulle-ilmoitukselle vastaus))
  (tiedot/alusta-uusi-kuittaus ilmoitukset/valittu-ilmoitus)
  (reset! ilmoitukset/uusi-kuittaus-auki? false))

(defn luo-henkilolomakeryhma [avain nimi]
  (let [avain (fn [suffix] (keyword (str avain "-" (name suffix))))]
    (lomake/ryhma {:otsikko    nimi
                   :leveys-col 3}
                  {:nimi       (avain :etunimi)
                   :ulkoasu    :rivi
                   :otsikko    "Etunimi"
                   :leveys-col 3
                   :tyyppi     :string}
                  {:nimi       (avain :sukunimi)
                   :otsikko    "Sukunimi"
                   :leveys-col 3
                   :tyyppi     :string}
                  {:nimi       (avain :matkapuhelin)
                   :otsikko    "Matkapuhelin"
                   :leveys-col 3
                   :tyyppi     :puhelin}
                  {:nimi       (avain :tyopuhelin)
                   :otsikko    "Työpuhelin"
                   :leveys-col 3
                   :tyyppi     :puhelin}
                  {:nimi       (avain :sahkoposti)
                   :otsikko    "Sähköposti"
                   :leveys-col 3
                   :tyyppi     :email}
                  {:nimi       (avain :organisaatio)
                   :otsikko    "Organisaation nimi"
                   :leveys-col 3
                   :tyyppi     :string}
                  {:nimi       (avain :ytunnus)
                   :otsikko    "Organisaation y-tunnus"
                   :leveys-col 3
                   :tyyppi     :string})))

(defn luo-lomake []
  [:div
   {:class "uusi-kuittaus"}
   [lomake/lomake
    {:muokkaa! #(reset! tiedot/uusi-kuittaus %)
     :luokka   :horizontal
     :footer   [:div
                [napit/palvelinkutsu-nappi
                 "Lähetä"
                 #(tiedot/tallenna-uusi-kuittaus @tiedot/uusi-kuittaus)
                 {:ikoni        (ikonit/tallenna)
                  :disabled     false
                  :kun-onnistuu (fn [vastaus] (kasittele-kuittauskasityksen-vastaus vastaus))
                  :virheviesti  "Kuittauksen tallennuksessa tai lähetyksessä T-LOIK:n tapahtui virhe."
                  :luokka       "nappi-ensisijainen"}]
                [napit/peruuta
                 "Peruuta"
                 #(swap! ilmoitukset/uusi-kuittaus-auki? not)]]}
    [(lomake/ryhma {:otsikko    "Kuittaus"
                    :leveys-col 3}

                   {:nimi          :tyyppi
                    :otsikko       "Tyyppi"
                    :pakollinen?   true
                    :tyyppi        :valinta
                    :valinnat      apurit/kuittaustyypit
                    :valinta-nayta apurit/kuittaustyypin-selite
                    :leveys-col    3}
                   {:nimi        :vapaateksti
                    :otsikko     "Vapaateksti"
                    :pakollinen? true
                    :tyyppi      :text
                    :leveys-col  3})
     #_[napit/avattava kasittelija-auki? "Käsittelijä" #(luo-henkilolomakeryhma "kasittelija" "Käsittelijä")]

     (luo-henkilolomakeryhma "kasittelija" "Käsittelijä")]
    @tiedot/uusi-kuittaus]])

(defn uusi-kuittaus-lomake []
  (komp/luo
    luo-lomake))

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

