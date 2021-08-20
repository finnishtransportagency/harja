(ns harja.views.urakka.lupaukset.kuukausipaatos-tilat
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]))

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

(defn kuukauden-nimi [kk vuosi kuluva-kuukausi kuluva-vuosi]
  (let [kuluva-kuukausi? (and (= kk kuluva-kuukausi)
                              (= vuosi kuluva-vuosi))]
    [:div.kk-nimi {:class (str (when kuluva-kuukausi? "vahva"))} (pvm/kuukauden-lyhyt-nimi kk)]))

(defn kuukaudelle-ei-voi-antaa-vastausta [kohdekuukausi vastaus]
  [:div (gstring/unescapeEntities "&nbsp;")])

(defn kuukaudelle-ei-paatosta-viela [kohdekuukausi vastaus]
  [:div "--"])

(defn odottaa-vastausta [_]
  [:div {:style {:color "#FFC300"}} [ikonit/harja-icon-status-help]])

(defn hyvaksytty-vastaus [_]
  [:div {:style {:color "#1C891C"}} [ikonit/harja-icon-status-selected]])

(defn hylatty-vastaus [_]
  [:div {:style {:color "#B40A14"}} [ikonit/harja-icon-status-denied]])

(defn kuukausi-wrapper [e! kohdekuukausi kohdevuosi vastaus vastauskuukausi listauksessa?]
  (let [kk-nyt (pvm/kuukausi (pvm/nyt))
        vuosi-nyt (pvm/vuosi (pvm/nyt))
        vastaukset (:vastaukset vastaus)
        vastaus-olemassa? (if (some #(and (= kohdekuukausi (:kuukausi %))
                                          (:vastaus %)) vastaukset)
                            true false)
        vastaus-hylatty? (if (some #(and (= kohdekuukausi (:kuukausi %))
                                         (false? (:vastaus %))) vastaukset)
                           true false)
        pisteet (first (keep (fn [vastaus]
                               (when (= kohdekuukausi (:kuukausi vastaus))
                                 (:pisteet vastaus)))
                             vastaukset))
        ;; Kun kuukaudelle voi tehdä kirjauksen, jos sille ei ole vielä tehty ja se on listassa jonka avian on :kirjaus-kkt tai kuukaudelle tehdään päätös
        kk-odottaa-vastausta? (if (and (false? vastaus-olemassa?) (false? vastaus-hylatty?) (nil? pisteet)
                                       (or (some #(= kohdekuukausi %) (:kirjaus-kkt vastaus))
                                           (= kohdekuukausi (:paatos-kk vastaus))))
                                true false)
        ;; Tulevaisuudessa oleville kuukausille ei voi antaa vastausta, niin tarkistetaan onko kohdekuukausi tulevaisuudessa
        kohdekk-tuleivaisuudessa? (if (or (> kohdevuosi vuosi-nyt)
                                          (and (= kohdevuosi vuosi-nyt)
                                               (> kohdekuukausi kk-nyt)))
                                    true false)]
    [:div.col-xs-1.pallo-ja-kk (merge {:class (str (when (= kohdekuukausi (:paatos-kk vastaus)) " paatoskuukausi")
                                             (when (= kohdekuukausi vastauskuukausi) " vastaus-kk")
                                             (when (true? kk-odottaa-vastausta?)
                                               " voi-valita"))}
                                      (when listauksessa?
                                        {:on-click (fn [e]
                                                     (do
                                                       (.preventDefault e)
                                                       (e! (lupaus-tiedot/->AvaaLupausvastaus vastaus kohdekuukausi))))}))
     (cond
       (and (true? kk-odottaa-vastausta?)
            (false? kohdekk-tuleivaisuudessa?)) [odottaa-vastausta kohdekuukausi]
       (and (true? kk-odottaa-vastausta?)
            (true? kohdekk-tuleivaisuudessa?)) [kuukaudelle-ei-paatosta-viela kohdekuukausi vastaus]
       (and (false? vastaus-olemassa?)
            (false? vastaus-hylatty?)
            (false? kk-odottaa-vastausta?)
            (nil? pisteet)) [kuukaudelle-ei-voi-antaa-vastausta kohdekuukausi vastaus]
       (true? vastaus-olemassa?) [hyvaksytty-vastaus kohdekuukausi]
       (true? vastaus-hylatty?) [hylatty-vastaus kohdekuukausi]
       (not (nil? pisteet)) [:div.kuukausi-pisteet pisteet]
       :else [hylatty-vastaus kohdekuukausi])
     [kuukauden-nimi kohdekuukausi kohdevuosi kk-nyt vuosi-nyt]]))