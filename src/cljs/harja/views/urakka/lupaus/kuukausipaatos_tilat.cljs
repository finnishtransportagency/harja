(ns harja.views.urakka.lupaus.kuukausipaatos-tilat
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.validointi :as v]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.urakka.lupaus-tiedot :as lupaus-tiedot]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]))

(defn paattele-kohdevuosi [kohdekuukausi vastaukset app]
  (let [kohdevuosi (:vuosi (first (filter (fn [v]
                                            (when (= kohdekuukausi (:kuukausi v))
                                              v)) vastaukset)))]
    ;; Jos vastausta ei ole ennettu, kohdevuosi on nil, kun se yritetään kaivaa olemattomasta vastauksesta - joten päätellään kohdevuosi valitusta hoitokaudesta
    (if-not (nil? kohdevuosi)
      kohdevuosi
      (if (>= kohdekuukausi 10)
        (pvm/vuosi (first (:valittu-hoitokausi app)))
        (pvm/vuosi (second (:valittu-hoitokausi app)))))))

(defn kuukauden-nimi [kk kuluva-kuukausi? ei-voi-vastata?]
  [:div.kk-nimi {:class (str (when kuluva-kuukausi? " lihavoitu")
                             (when ei-voi-vastata? " himmennetty"))}
   (pvm/kuukauden-lyhyt-nimi kk)])

(defn kuukaudelle-ei-voi-antaa-vastausta []
  [:div (gstring/unescapeEntities "&nbsp;")])

(defn odottaa-vastausta []
  [:div {:style {:color "#FFC300"}} [ikonit/harja-icon-status-help]])

(defn voi-vastata-tulevaisuudessa []
  [:div [ikonit/harja-icon-action-subtract]])

(defn hyvaksytty-vastaus []
  [:div {:style {:color "#1C891C"}} [ikonit/harja-icon-status-selected]])

(defn hylatty-vastaus []
  [:div {:style {:color (asioiden-ulkoasu/tilan-vari "hylatty")}} [ikonit/harja-icon-status-denied]])

(defn kuukausi-wrapper [e!
                        {:keys [lupaus-id] :as lupaus}
                        {:keys [kuukausi vuosi odottaa-kannanottoa? paatos-hylatty? paattava-kuukausi? nykyhetkeen-verrattuna vastaus] :as lupaus-kuukausi}
                        listauksessa?
                        valittu?
                        lupaus->kuukausi->kommentit]
  (let [vastauskuukausi? (lupaus-domain/vastauskuukausi? lupaus-kuukausi)
        saa-vastata? (lupaus-domain/kayttaja-saa-vastata? @istunto/kayttaja lupaus-kuukausi)
        nayta-himmennettyna? (not saa-vastata?)
        nayta-kommentti-ikoni? (and (not listauksessa?)
                                    (seq (get-in lupaus->kuukausi->kommentit [lupaus-id kuukausi])))]
    [:div.col-xs-1.pallo-ja-kk.ei-sulje-sivupaneelia
     (merge {:class (str (when paattava-kuukausi? " paatoskuukausi")
                         (when valittu? " vastaus-kk")
                         (when saa-vastata? " voi-valita"))}
            (when (and listauksessa? saa-vastata?)
              {:on-click (fn [e]
                           (do
                             (.preventDefault e)
                             (e! (lupaus-tiedot/->AvaaLupausvastaus lupaus kuukausi vuosi))))}))
     (cond
       odottaa-kannanottoa?
       [odottaa-vastausta]

       ;; Tälle kuukaudelle ei voi antaa vastausta ollenkaan
       (not vastauskuukausi?)
       [kuukaudelle-ei-voi-antaa-vastausta]

       ;; KE vastauksen kuukausi, jossa on hyväksytty tulos
       (true? (:vastaus vastaus))
       [hyvaksytty-vastaus]

       ;; KE vastauksen kuukausi, jossa on hylätty tulos
       (false? (:vastaus vastaus))
       [hylatty-vastaus]

       ;; Monivalinta vastauksen kuukausi, jossa on pisteet
       (and (:lupaus-vaihtoehto-id vastaus)
            (:pisteet vastaus))
       [:div.kuukausi-pisteet (:pisteet vastaus)]

       ;; Joustovara on ylittynyt
       paatos-hylatty?
       [kuukaudelle-ei-voi-antaa-vastausta]

       ;; Kuukaudelle voi antaa vastauksen, mutta se on tulevaisuudessa
       (and vastauskuukausi? (= :tuleva-kuukausi nykyhetkeen-verrattuna))
       [voi-vastata-tulevaisuudessa]

       ;; Laitetaan kaikissa muissa tapauksissa tyhjä laatikko
       :else
       [kuukaudelle-ei-voi-antaa-vastausta])

     [kuukauden-nimi kuukausi (= :kuluva-kuukausi nykyhetkeen-verrattuna) nayta-himmennettyna?]
     (when nayta-kommentti-ikoni?
       [:div.kk-on-kommentteja
        [ikonit/harja-icon-action-message-filled]])]))

(defn kuukauden-pisteet [e! app id kuukausi vuosi tyyppi urakka pisteet disabled?]
  (let [app (assoc-in app [:kuukausipisteet-ehdotus (keyword (str kuukausi))] pisteet)]
    [:div {:style {:width "36px"
                   :margin "auto"}}
     [kentat/tee-kentta {:elementin-id (str kuukausi "-" vuosi)
                         :tyyppi :positiivinen-numero
                         :koko 3
                         :vayla-tyyli? true
                         :input-luokka "kuukausipisteet"
                         :kokonaisluku? true
                         :disabled? disabled?
                         :validoi-kentta-fn (fn [numero] (v/validoi-numero numero 0 100 1))
                         :on-key-down #(when (or (= 13 (-> % .-keyCode)) (= 13 (-> % .-which)))
                                         (e! (lupaus-tiedot/->TallennaKuukausipisteet id kuukausi vuosi tyyppi urakka)))
                         :on-blur #(e! (lupaus-tiedot/->TallennaKuukausipisteet id kuukausi vuosi tyyppi urakka))}
      (r/wrap (get-in app [:kuukausipisteet-ehdotus (keyword (str kuukausi))])
              (fn [p]
                (e! (lupaus-tiedot/->Kuukausipisteitamuokattu p kuukausi))))]]))

(defn kuukausiennuste [e! app {:keys [id pisteet kuukausi vuosi tyyppi kuluva-kuukausi? voi-vastata? odottaa-vastausta? avattu-muokattavaksi?] :as kuukausipisteet} urakka]
  (let [nayta-himmennettyna? (not voi-vastata?)]
    ;; Aseta focus input kenttään, jos muokkaustila on laitettu päälle
    (when avattu-muokattavaksi?
      (harja.ui.yleiset/fn-viiveella
        #(some-> (.getElementById js/document (str kuukausi "-" vuosi)) .focus)
        200))
    [:div {:style {:margin-left "8px"}}
     [:div {:style {:text-align "center"}}
      (cond (= "toteuma" tyyppi) "Toteuma"
            (= 3 kuukausi) "Ennusteet"
            :else (gstring/unescapeEntities "&nbsp;"))]
     [:div.col-xs-1.pallo-ja-kk {:style {:border "1px solid gray" :width "55px"}}
      (cond
        (and odottaa-vastausta? (not avattu-muokattavaksi?))
        [:div {:style {:color "#FFC300"
                       :cursor "pointer"
                       :height "44px"
                       :padding "16px"}
               :on-click #(e! (lupaus-tiedot/->AvaaKuukausipisteetMuokattavaksi kuukausi))}
         [ikonit/harja-icon-status-help]]
        (or (and voi-vastata? (nil? pisteet)) avattu-muokattavaksi?)
        [kuukauden-pisteet e! app id kuukausi vuosi tyyppi urakka pisteet (not voi-vastata?)]
        (and voi-vastata? pisteet)
        [:div.kuukausi-pisteet
         {:style {:cursor "pointer"
                  :height "44px"
                  :padding "16px"}
          :on-click #(e! (lupaus-tiedot/->AvaaKuukausipisteetMuokattavaksi kuukausi))}
         pisteet]
        (and (not voi-vastata?) (nil? pisteet))
        [:div {:style {:height "44px"
                       :padding "16px"}}
         [ikonit/harja-icon-action-subtract]]
        :else
        [:div.kuukausi-pisteet {:style {:height "44px"
                                        :padding "16px"}}
         pisteet])
      [kuukauden-nimi kuukausi kuluva-kuukausi? nayta-himmennettyna?]]]))