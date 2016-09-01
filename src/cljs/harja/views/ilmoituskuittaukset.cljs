(ns harja.views.ilmoituskuittaukset
  "Harjan ilmoituskuittausten listaus & uuden kuittauksen kirjaus lomake."
  (:require [clojure.string :refer [capitalize]]
            [reagent.core :refer [atom]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoituskuittaukset :as tiedot]
            [harja.domain.ilmoitukset :as apurit]
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
            [harja.ui.viesti :as viesti]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn kasittele-kuittauskasityksen-vastaus [vastaus]
  (when vastaus
    (viesti/nayta! "Kuittaus lähetetty Tieliikennekeskukseen." :success)
    (ilmoitukset/lisaa-kuittaus-valitulle-ilmoitukselle vastaus))
  (tiedot/alusta-uusi-kuittaus ilmoitukset/valittu-ilmoitus)
  (ilmoitukset/sulje-uusi-kuittaus!))

(defn esta-lahetys? []
  (let [kuittaus @tiedot/uusi-kuittaus]
    (or (empty? (:vapaateksti kuittaus))
        (not (some #(= (:tyyppi kuittaus) %) apurit/kuittaustyypit)))))

(defn uusi-kuittaus []
  [:div
   {:class "uusi-kuittaus"}
   [lomake/lomake
    {:muokkaa! (fn [uusi-data]
                 (let [kasitelty-lomakedata (tiedot/tarkista-ja-paivita-vapaateksti uusi-data @tiedot/uusi-kuittaus)]
                   (reset! tiedot/uusi-kuittaus kasitelty-lomakedata)))
     :luokka :horizontal
     :footer [:div
              [napit/palvelinkutsu-nappi
               "Lähetä"
               #(tiedot/laheta-uusi-kuittaus @tiedot/uusi-kuittaus)
               {:ikoni (ikonit/tallenna)
                :disabled (esta-lahetys?)
                :kun-onnistuu (fn [vastaus]
                                (kasittele-kuittauskasityksen-vastaus vastaus)
                                (tapahtumat/julkaise!
                                  {:aihe :ilmoituksen-kuittaustiedot-päivitetty
                                   :id (:id @ilmoitukset/valittu-ilmoitus)}))
                :virheviesti "Kuittauksen tallennuksessa tai lähetyksessä T-LOIK:n tapahtui virhe."
                :luokka "nappi-ensisijainen"}]
              [napit/peruuta
               "Peruuta"
               #(do
                 (ilmoitukset/sulje-uusi-kuittaus!)
                 (tiedot/alusta-uusi-kuittaus ilmoitukset/valittu-ilmoitus))
               {:luokka "pull-right"}]]}
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
     (lomake/ryhma {:otsikko    "Käsittelijä"
                    :leveys-col 3}
                   {:nimi       :kasittelija-etunimi
                    :otsikko    "Etunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-sukunimi
                    :otsikko    "Sukunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-matkapuhelin
                    :otsikko    "Matkapuhelin"
                    :leveys-col 3
                    :tyyppi     :puhelin}
                   {:nimi       :kasittelija-tyopuhelin
                    :otsikko    "Työpuhelin"
                    :leveys-col 3
                    :tyyppi     :puhelin}
                   {:nimi       :kasittelija-sahkoposti
                    :otsikko    "Sähköposti"
                    :leveys-col 3
                    :tyyppi     :email}
                   {:nimi       :kasittelija-organisaatio
                    :otsikko    "Organisaation nimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-ytunnus
                    :otsikko    "Organisaation y-tunnus"
                    :leveys-col 3
                    :tyyppi     :string})]
    @tiedot/uusi-kuittaus]])

(defn uusi-kuittaus-lomake []
  (komp/luo
    uusi-kuittaus))

(defn kuittauksen-tiedot [kuittaus]
  ^{:key (str "kuittaus-paneeli-" (:id kuittaus))}
  [bs/panel
   {:class "kuittaus-viesti"}
   (capitalize (name (:kuittaustyyppi kuittaus)))
   [:span
    ^{:key "kuitattu"}
    [yleiset/tietoja {}
     "Kuitattu: " (pvm/pvm-aika-sek (:kuitattu kuittaus))
     "Lisätiedot: " (:vapaateksti kuittaus)]
    [:br]
    ^{:key "kuittaaja"}
    [yleiset/tietoja {}
     "Kuittaaja: " (apurit/nayta-henkilo (:kuittaaja kuittaus))
     "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kuittaaja kuittaus))
     "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]
    [:br]
    (when (:kasittelija kuittaus)
      ^{:key "kasittelija"}
      [yleiset/tietoja {}
       "Käsittelijä: " (apurit/nayta-henkilo (:kasittelija kuittaus))
       "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kasittelija kuittaus))
       "Sähköposti: " (get-in kuittaus [:kasittelija :sahkoposti])])]])

(defn kuittaa-monta-lomake [{:keys [ilmoitukset tyyppi vapaateksti] :as data}
                            muokkaa!
                            kuittaukset-tallennettu]
  (let [valittuna (count ilmoitukset)]
    [:div.ilmoitukset-kuittaa-monta
     [lomake/lomake
      {:muokkaa! muokkaa!
       :palstoja 2
       :otsikko "Kuittaa monta ilmoitusta"}
      [{:otsikko "Kuittaustyyppi"
        :pakollinen? true
        :tyyppi :valinta
        :valinnat apurit/kuittaustyypit
        :valinta-nayta #(or (apurit/kuittaustyypin-selite %) "- Valitse kuittaustyyppi -")
        :nimi :tyyppi}

       {:otsikko "Vapaateksti"
        :pakollinen? true
        :tyyppi :text
        :koko [80 :auto]
        :nimi :vapaateksti}]

      data]
     [napit/palvelinkutsu-nappi
      (if (> valittuna 1)
        (str "Kuittaa " valittuna " ilmoitusta")
        "Kuittaa ilmoitus")
      #(go (<! (tiedot/laheta-kuittaukset! ilmoitukset {:tyyppi tyyppi
                                                        :vapaateksti vapaateksti}))
           (kuittaukset-tallennettu))

      {:luokka   "nappi-ensisijainen kuittaa-monta-tallennus"
       :disabled (or (not (lomake/voi-tallentaa-ja-muokattu? data))
                     (zero? valittuna))}]
     [napit/peruuta "Peruuta" #(muokkaa! nil)]
     [yleiset/vihje "Valitse kuitattavat ilmoitukset listalta."]]))
