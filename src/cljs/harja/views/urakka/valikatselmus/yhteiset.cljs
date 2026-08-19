(ns harja.views.urakka.valikatselmus.yhteiset
  (:require [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]))


;; TODO poistuu ehkä
(defn paatoksen-poistovarmistus-modaali
  [{:keys [peru-paatos-fn teksti]}]
  (let [varmistus-modaalin-kutsu-fn #(modal/nayta! {:otsikko "Perutaanko päätös?"
                                                    :otsikko-muotoilut {:font-size "28px" :margin-bottom "24px"}
                                                    :leveys "560px"
                                                    :content-tyyli {:line-height "24px" :padding-top "24px" :padding-bottom "24px"}
                                                    :body-tyyli {:margin-bottom "24px"}
                                                    :footer [:div
                                                             [napit/yleinen-ensisijainen "Peru päätös" (fn []
                                                                                                         (modal/piilota!)
                                                                                                         (peru-paatos-fn)) {:luokka "valikatselmus-nappi nappi-ensisijainen"}]
                                                             [napit/yleinen-toissijainen "Peruuta" (fn [] (modal/piilota!)) {:luokka "valikatselmus-nappi nappi-toissijainen"}]]
                                                    :footer-tyyli {:text-align "left"}
                                                    :ruksi-tyyli {:color "#004D99" :font-size "24px"}}
                                       [:div teksti])]
    varmistus-modaalin-kutsu-fn))

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
  [:div.paatos-komponentti-otsikko-row.klikattava {:on-click #(e! avaa-paatos-fn)}
   [:div.navigation-ikoni {:on-click #(avaa-tai-sulje-haitari-fn % paatos-avain)}
    ;; Kun päätosavainta ei löydy setistä, niin pidetään päätös avattuna (defaulttina kaikki on auki)
    (if (not (contains? avatut-paatokset paatos-avain))
      [ikonit/navigation-ympyrassa :up {:aria-label "Sulje päätöskomponentti" :alt "Sulje päätöskomponentti"}]
      [ikonit/navigation-ympyrassa :down {:aria-label "Avaa päätöskomponentti" :alt "Avaa päätöskomponentti"}])]
   [:h2.paatos-komponentti-otsikko otsikko]
   [:div
    (if paatos-tehty?
      [:div.badge.paatetty.paatos-badge "Päätetty"]
      [:div.badge.avoin.paatos-badge "Avoin"])]])


(defn paatosotsikko [otsikko paatos-tehty?]
  [:div.paatos-komponentti-otsikko-row
   [:h2.paatos-komponentti-otsikko otsikko]
   [:div
    (if paatos-tehty?
      [:div.badge.paatetty.paatos-badge "Päätetty"]
      [:div.badge.avoin.paatos-badge "Avoin"])]])


(defn paatosnapit [paatos-tehty? on-oikeudet? paatoksen-tiedot tallennus-kesken? voi-muokata? tallenna-paatos-fn poista-paatos-fn ei-oikeuksia-teksti-fn]
  (if (not paatos-tehty?)
    [:div.paatos-toiminto
     (if on-oikeudet?
       [napit/yleinen-ensisijainen "Vahvista päätös" tallenna-paatos-fn
        {:ikoni [ikonit/harja-icon-status-selected]
         :disabled (or tallennus-kesken? (not voi-muokata?) (:virhe paatoksen-tiedot))}]
       (when ei-oikeuksia-teksti-fn (ei-oikeuksia-teksti-fn)))]
    [:div.paatos-toiminto
     (if on-oikeudet?
       [napit/nappi
        "Peru päätös"
        poista-paatos-fn
        {:luokka "nappi-toissijainen"
         :ikoni [ikonit/harja-icon-action-undo]
         :disabled (or tallennus-kesken? (not voi-muokata?))}]
       (when ei-oikeuksia-teksti-fn (ei-oikeuksia-teksti-fn)))]))
