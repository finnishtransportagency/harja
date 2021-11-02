(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.osion-vahvistus
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]
            [harja.ui.modal :as modal]
            [harja.ui.debug :as debug]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.urakka :as tila]
            [clojure.string :as str]))


(defn muutosten-vahvistus-modal
  [laheta-fn! muuta-fn! vahvistus]
  [modal/modal {:otsikko "Sun pitää vahvistaa tää"
                :nakyvissa? true
                :sulje-fn #(e! (t/->SuljeMuutostenVahvistusModal))}
   [:div "Please confirm"
    [:div "vahvistus" [debug/debug vahvistus]]
    (for [v (keys (:vahvistettavat-vuodet vahvistus))]
      [:div
       [:h3 (str "vuosi " v)]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :muutoksen-syy)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :selite)}]
       [:input {:type :text :on-blur (r/partial muuta-fn! v :maara)}]])
    [:button {:on-click (r/partial laheta-fn! e! (:tiedot vahvistus))} "Klikkeris"]]])

(defn muutokset-estetty-modal
  [laheta-fn! muuta-fn! vahvistus]
  [modal/modal {:otsikko "Muutoksia ei voi tehdä vahvistettuun osioon"
                :nakyvissa? true
                :sulje-fn #(e! (t/->SuljeMuutostenVahvistusModal))}
   [:div
    [:div "Muutoksia ei voi tehdä jo vahvistettuun osioon." [:br] [:b "Tekemiäsi muutoksia ei tallenneta."]]
    [:br] [:br]
    [:div "Vahvistetun osion muutosten tekemiseen ja muutosten seurantaan liittyvät ominaisuudet julkaistaan myöhemmin."]]])


(defn- osio-vahvistettu?
  [osioiden-tilat osio-kw hoitovuosi-nro]
  (boolean (get-in osioiden-tilat [osio-kw hoitovuosi-nro])))

(defn- vaaditut-osiot-vahvistettu?
  [osioiden-tilat vaaditut-osiot hoitovuosi-nro]
  (if (seq vaaditut-osiot)
    (every? #(osio-vahvistettu? osioiden-tilat % hoitovuosi-nro) vaaditut-osiot)
    true))

(defn- vahvistamattomat-vaaditut-osiot
  [osioiden-tilat vaaditut-osiot hoitovuosi-nro]
  (filter #(not (osio-vahvistettu? osioiden-tilat % hoitovuosi-nro)) vaaditut-osiot))

(defn vahvista-osio-komponentti
  "Komponentilla vahvistetaan yksittäinen kustannussuunnitelman osio.
  Osio-kw: Vahvistettavan osion uniikki keyword-tunniste.
  Osioiden-tilat: Osioiden app-tilat. Eli, onko osio vahvistettu tietylle hoitovuodelle vai ei.
  Vaaditut-osiot: Osioiden tunnisteet sekvensissä, joiden täytyy olla vahvistettu ennen kuin tämän osion voi vahvistaa.
  "
  [osio-kw {:keys [osioiden-tilat vahvistus-vaadittu-osiot hoitovuosi-nro indeksit-saatavilla?
                   osiossa-virheita?] :as opts}]
  (let [auki? (r/atom false)
        tilaa-muutettu? false
        vahvista-suunnitelman-osa-fn (fn [tyyppi hoitovuosi]
                                       (e! (t/->VahvistaSuunnitelmanOsioVuodella {:tyyppi tyyppi
                                                                                  :hoitovuosi hoitovuosi})))
        kumoa-osion-vahvistus-fn (fn [tyyppi hoitovuosi]
                                   (e! (t/->KumoaOsionVahvistusVuodelta {:tyyppi tyyppi
                                                                         :hoitovuosi hoitovuosi})))
        avaa-tai-sulje #(swap! auki? not)]
    (fn [osio-kw {:keys [osioiden-tilat vahvistus-vaadittu-osiot hoitovuosi-nro indeksit-saatavilla? osiossa-virheita?] :as opts}]
      (let [vahvistettu? (osio-vahvistettu? osioiden-tilat osio-kw hoitovuosi-nro)
            vaaditut-vahvistettu? (vaaditut-osiot-vahvistettu? osioiden-tilat vahvistus-vaadittu-osiot hoitovuosi-nro)
            oikeus-vahvistaa? (ks-yhteiset/oikeus-vahvistaa-osio?
                                @istunto/kayttaja
                                (some-> @tila/yleiset :urakka :id))]
        [:div.vahvista-osio {:class (cond
                                      (not indeksit-saatavilla?)
                                      "indeksit-puuttuvat"
                                      vahvistettu?
                                      "vahvistettu")
                             :data-cy (str "vahvista-osio-" (name osio-kw))
                             :on-click avaa-tai-sulje}

         ;; Laatikon otsikko
         [:div.otsikko
          ;; TODO: Harja svg-ikoneista kannattaisi tehdä sprite, jolloin värivariantteja olisi helpompi tehdä.
          ;;       Nyt tässä käytetään svg-ikoneita yksittäisinä kuvina.
          (cond
            (not indeksit-saatavilla?)
            [ikonit/misc-document-confirm-svg]
            vahvistettu?
            [ikonit/status-completed-svg]
            :else
            [ikonit/misc-document-confirm-svg])
          [:div (cond
                  (not indeksit-saatavilla?)
                  "Suunnitelman voi vahvistaa lokakuussa, jolloin Harja laskee indeksikorjaukset."

                  (not vaaditut-vahvistettu?)
                  [:div
                   [:div "Tätä osiota ei voi vahvistaa ennen kuin seuraavat osiot on tarkastettu ja vahvistettu: "]
                   [:div (str/join ", "
                           (map (comp str/capitalize #(str/replace % #"-" " ") name)
                             (vahvistamattomat-vaaditut-osiot osioiden-tilat vahvistus-vaadittu-osiot hoitovuosi-nro)))]]

                  vahvistettu?
                  (str "Suunnitelma ja " hoitovuosi-nro ". hoitovuoden indeksikorjaukset on vahvistettu.")

                  :else
                  (str "Vahvista suunnitelma ja " hoitovuosi-nro ". hoitovuoden indeksikorjaukset."))]

          ;; Laatikon voi laajentaa vain jos indeksit ovat saatavilla ja vaaditut osiot on vahvistettu.
          (when (and indeksit-saatavilla? vaaditut-vahvistettu?)
            [:div.laajenna-btn
             {:class (when vahvistettu? "vahvistettu")}
             (if @auki?
               [:<> [ikonit/livicon-chevron-up] "Pienennä"]
               [:<> [ikonit/livicon-chevron-down] "Lisätiedot"])])]

         (when @auki?
           [:<>
            ;; Debug-rivit
            #_[:div.flex-row
               [yleiset/placeholder (pr-str @istunto/kayttaja)]
               [yleiset/placeholder
                (str
                  " 1. Vahvistettava osio: " osio-kw
                  " 2. oikeus-vahvistaa?: " oikeus-vahvistaa?
                  " 3. osio-vahvistettu?: " vahvistettu?
                  " 4. Indeksit-saatavilla?: " indeksit-saatavilla?
                  " 5. Tilaajan käyttäjä?: " (roolit/tilaajan-kayttaja? @istunto/kayttaja))]]

            ;; Seliteosio
            (when indeksit-saatavilla?
              [:div.selite
               (if vahvistettu?
                 [:<>
                  [:div "Jos suunnitelmaa muutetaan tämän jälkeen, ei erotukselle tehdä enää indeksikorjausta."]
                  [:div "Indeksikorjaus on laskettu vain alkuperäiseen lukuun."]]
                 [:<>
                  [:div "Vahvistamalla vahvistat indeksikorjaukset ko. hoitovuodelle."]
                  [:div "Jos suunnitelmaa muutetaan tämän jälkeen, ei erotukselle tehdä enää indeksikorjausta.
                  Indeksikorjaus on laskettu vain alkuperäiseen lukuun."]])])

            ;; Kontrollit
            (when (and
                    ;; Jos vaaditut osiot eivät ole vahvistettu, niin ei näytetä kontrolleja.

                    vaaditut-vahvistettu?
                    ;; Jos indeksit eivät ole saatavilla, niin ei näytetä kontrolleja.
                    indeksit-saatavilla?
                    ;; Jos käyttäjän rooli ei ole riittävä, niin vahvistetulle osiolle ei näytetä kontrolleja.
                    (or (not vahvistettu?) (and vahvistettu? oikeus-vahvistaa?)))
              [:div.kontrollit
               ;; Aluevastaava voi kumota vahvistuksen niin kauan kun vahvistettuun osuuteen ei ole tullut mitään muutoksia.
               (if (and vahvistettu?
                     (not tilaa-muutettu?))
                 ;; Kumoa vahvistus
                 [napit/yleinen-ensisijainen "Kumoa vahvistus"
                  kumoa-osion-vahvistus-fn
                  {:data-attributes {:data-cy "kumoa-osion-vahvistus-btn"}
                   :toiminto-args [osio-kw hoitovuosi-nro]}]

                 ;; Vahvista
                 [:<>
                  [napit/yleinen-ensisijainen "Vahvista"
                   vahvista-suunnitelman-osa-fn
                   {:data-attributes {:data-cy "vahvista-osio-btn"}
                    :disabled (or (not oikeus-vahvistaa?) osiossa-virheita?)
                    :toiminto-args [osio-kw hoitovuosi-nro]}]
                  ;; Jos käyttäjän rooli ei ole riittävä, niin näytetään varoitus.
                  (when (not oikeus-vahvistaa?)
                    [:div.varoitus "Vain urakan aluevastaava voi vahvistaa suunnitelman"])
                  (when osiossa-virheita?
                    [:div.varoitus "Osiossa on virheitä, jotka on korjattava ennen kuin suunnitelman voi vahvistaa"])])])])]))))
