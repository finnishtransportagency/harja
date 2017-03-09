(ns harja.views.ilmoitukset.tietyoilmoituslomake
  (:require [reagent.core :as r]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [reagent.core :refer [atom] :as r]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                                  kuvaus-ja-avainarvopareja]]))

(def koskee-valinnat [[nil "Valitse..."]
                      [:ensimmainen "Ensimmäinen ilmoitus työstä"],
                      [:muutos "Korjaus/muutos aiempaan tietoon"],
                      [:tyovaihe "Työvaihetta koskeva ilmoitus"],
                      [:paattyminen "Työn päättymisilmoitus"]])


(defn- projekti-valinnat [urakat]
  (partition 2
             (interleave
               (mapv (comp str :id) urakat) (mapv :nimi urakat))))

(defn- pvm-vali-paivina [p1 p2]

  (let [pp1 (pvm/->pvm p1)
        pp2 (pvm/->pvm p2)]
    (log "pvm-vali-paivina kutsuttu" p1 p2 pp1 pp2)
    (when (and pp1 pp2)
      (/ (Math/abs (- p1 p2)) (* 1000 60 60 24)))))

(defn lomake [e! ilmoitus kayttajan-urakat]
  (fn [e! ilmoitus]
    [:div
     [:span
      [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (tiedot/->PoistaIlmoitusValinta))]
      [lomake/lomake {:otsikko "Muokkaa ilmoitusta"
                      :muokkaa #(e! tiedot/->IlmoitustaMuokattu %)}
       [(lomake/ryhma
          "Ilmoitus koskee"
          {:nimi :koskee
           :otsikko "Ilmoitus koskee:"
           :tyyppi :valinta
           :valinnat koskee-valinnat
           :valinta-nayta second
           :valinta-arvo first
           :muokattava? (constantly true)}
          )
        (lomake/ryhma "Tiedot koko kohteesta"
                      {:nimi :projekti-tai-urakka
                       :otsikko "Projekti tai urakka:"
                       :tyyppi :valinta
                       :valinnat (projekti-valinnat kayttajan-urakat)
                       :valinta-nayta second :valinta-arvo first
                       :muokattava? (constantly true)}
                      {:nimi :urakoitsijan-nimi
                       :otsikko "Urakoitsijan nimi"
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :urakoitsijan-yhteyshenkilo
                       :otsikko "Urakoitsijan yhteyshenkilö"
                       :hae #(str (:urakoitsijayhteyshenkilo_etunimi %) " " (:urakoitsijayhteyshenkilo_sukunimi %))
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :urakoitsijayhteyshenkilo_matkapuhelin
                       :otsikko "Puhelinnumero"
                       :tyyppi :string}
                      {:nimi :tilaajan-nimi
                       :otsikko "Tilaajan nimi"
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :tilaajan-yhteyshenkilo
                       :otsikko "Tilaajan yhteyshenkilö"
                       :hae #(str (:tilaajayhteyshenkilo_etunimi %) " " (:tilaajayhteyshenkilo_sukunimi %))
                       :muokattava? (constantly true)
                       :tyyppi :string}
                      {:nimi :tilaajayhteyshenkilo_matkapuhelin
                       :otsikko "Puhelinnumero"
                       :tyyppi :string}
                      {:nimi :tr_numero
                       :otsikko "Tienumero"
                       :tyyppi :positiivinen-numero
                       :muokattava? (constantly true)}
                      {:otsikko "Tierekisteriosoite"
                       :nimi :tr-osoite
                       :pakollinen? true
                       :tyyppi :tierekisteriosoite
                       :ala-nayta-virhetta-komponentissa? true
                       :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:sijainti]]]
                       :sijainti (r/wrap (:sijainti lomake)
                                         #(log "laitettas sijainti" (pr-str %)))}
                      {:otsikko "Tien nimi" :nimi :tien_nimi
                       :tyyppi :string}
                      {:otsikko "Kunta/kunnat" :nimi :kunnat
                       :tyyppi :string}
                      {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi :alkusijainnin_kuvaus
                       :tyyppi :string}
                      {:otsikko "Työn aloituspvm" :nimi :alku :tyyppi :pvm}
                      {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi :loppusijainnin_kuvaus
                       :tyyppi :string}
                      {:otsikko "Työn lopetuspvm" :nimi :loppu :tyyppi :pvm}
                      {:otsikko "Työn pituus" :nimi :tyon-pituus
                       :tyyppi :positiivinen-numero
                       :hae #(pvm-vali-paivina (:alku %) (:loppu %))})

        (lomake/ryhma "Työvaihe")
        (lomake/ryhma "Työn tyyppi")
        (lomake/ryhma "Työaika")
        (lomake/ryhma "Vaikutukset liikenteelle")
        (lomake/ryhma "Vaikutussuunta")
        (lomake/ryhma "Muuta")]
       ilmoitus]]]))
