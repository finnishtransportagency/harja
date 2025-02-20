(ns harja.views.urakka.valikatselmus.yhteiset
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]))

;; Varmistetaan, että on kirjoitusoikeudet. Välikatselmusta voi katsoa lukuoikeuksilla, mutta päätöksiä ei voi tehdä
;; lukuoikeuksilla
(defn onko-oikeudet-tehda-paatos? [urakka-id]
  (oikeudet/voi-kirjoittaa? oikeudet/urakat-kulut-valikatselmus urakka-id @istunto/kayttaja))

(defn onko-hoitokausi-menneisyydessa?
  "Tulkitaan, että hoitokausi on menneisyydessä, jos se on päättynyt edellisenä vuonna. Eli jos nykyhetki on 31.12.2021
  ja hoitopäättyy 30.09.2021 niin hoitokausi ei ole vielä menneisyydessä. Tehdään tulkinta tässä vaiheessa niin, että
  käyttäjille jää n. 3kk aikaa tehdä välikatselmukset ja sen jälkeen se lukitaan."
  [hoitokausi nykyhetki urakan-alkuvuosi]
  ;; Niin moni urakka ei ole tehnyt välikatselmusta, että otetaan tarkistus hetkeksi pois käytöstä
  false
  #_(let [hoitokauden-loppuvuosi (pvm/vuosi (second hoitokausi))
          nykyvuosi (pvm/vuosi nykyhetki)
          vanha-mhu? (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)]
      (cond
        ;; Vanhemman MH urakat saa täyttää päätöksiä, vaikka hoitokausi olisi menneisyydessä
        (and vanha-mhu? (> nykyvuosi hoitokauden-loppuvuosi))
        false

        (and (not vanha-mhu?) (> nykyvuosi hoitokauden-loppuvuosi))
        true
        :else
        false)))

(defn paatosotsikko-ja-avaus [e! otsikko paatos-tehty? paatos-avain avatut-paatokset avaa-tai-sulje-haitari-fn avaa-paatos-fn]
  [:div.paatos-komponentti-otsikko-row {:on-click #(e! avaa-paatos-fn)}
   [:div.navigation-ikoni {:on-click #(avaa-tai-sulje-haitari-fn %)}
    ;; Kun päätosavainta ei löydy setistä, niin pidetään päätös avattuna (defaulttina kaikki on auki)
    (if (not (contains? avatut-paatokset paatos-avain))
      [ikonit/navigation-ympyrassa :up]
      [ikonit/navigation-ympyrassa :down])]
   [:h2.paatos-komponentti-otsikko otsikko]
   [:div
    (if paatos-tehty?
      [:div.badge.paatetty.paatos-badge "Päätetty"]
      [:div.badge.avoin.paatos-badge "Avoin"])]])

(defn paatosnapit [paatos-tehty? on-oikeudet? paatoksen-tiedot tallennus-kesken? voi-muokata? tallenna-paatos-fn poista-paatos-fn]
  (if (not paatos-tehty?)
    [:div.paatos-toiminto
     (when on-oikeudet?
       [napit/yleinen-ensisijainen "Tallenna päätös" tallenna-paatos-fn
        {:ikoni [ikonit/harja-icon-status-selected]
         :disabled (or tallennus-kesken? (not voi-muokata?))}])]
    [:div.paatos-toiminto
     (when on-oikeudet?
       [napit/nappi
        "Kumoa päätös"
        poista-paatos-fn
        {:luokka "nappi-toissijainen napiton-nappi"
         :ikoni [ikonit/harja-icon-action-undo]
         :disabled (or tallennus-kesken? (not voi-muokata?))}])]))
