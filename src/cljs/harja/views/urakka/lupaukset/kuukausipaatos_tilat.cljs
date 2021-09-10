(ns harja.views.urakka.lupaukset.kuukausipaatos-tilat
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.lupaukset :as ld]))

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
  [:div {:style {:color "#B40A14"}} [ikonit/harja-icon-status-denied]])

(defn kuukausi-wrapper [e!
                        {:keys [lupaus-id] :as lupaus}
                         {:keys [kuukausi vuosi odottaa-kannanottoa? paatos-hylatty? paattava-kuukausi? nykyhetkeen-verrattuna vastaus] :as lupaus-kuukausi}
                         listauksessa?
                         valittu?
                        lupaus->kuukausi->kommentit]
  (let [vastauskuukausi? (ld/vastauskuukausi? lupaus-kuukausi)
        saa-vastata? (ld/kayttaja-saa-vastata? @istunto/kayttaja lupaus-kuukausi)
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
        [ikonit/harja-icon-action-message]])]))