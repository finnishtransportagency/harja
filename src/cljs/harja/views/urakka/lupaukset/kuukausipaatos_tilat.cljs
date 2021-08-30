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

(defn kuukauden-nimi [kk vuosi kuluva-kuukausi kuluva-vuosi ei-voi-vastata?]
  (let [kuluva-kuukausi? (and (= kk kuluva-kuukausi)
                              (= vuosi kuluva-vuosi))]
    [:div.kk-nimi {:class (str (when (and (false? ei-voi-vastata?) kuluva-kuukausi?) " lihavoitu")
                               (when ei-voi-vastata? " himmennetty"))} (pvm/kuukauden-lyhyt-nimi kk)]))

(defn kuukaudelle-ei-voi-antaa-vastausta [kohdekuukausi vastaus]
  [:div (gstring/unescapeEntities "&nbsp;")])

(defn kuukaudelle-ei-paatosta-viela [kohdekuukausi vastaus]
  [:div [ikonit/harja-icon-action-subtract]])

(defn odottaa-vastausta [_]
  [:div {:style {:color "#FFC300"}} [ikonit/harja-icon-status-help]])

(defn hyvaksytty-vastaus [_]
  [:div {:style {:color "#1C891C"}} [ikonit/harja-icon-status-selected]])

(defn hylatty-vastaus [_]
  [:div {:style {:color "#B40A14"}} [ikonit/harja-icon-status-denied]])

(defn kuukausi-wrapper [e! kohdekuukausi kohdevuosi vastaus vastauskuukausi listauksessa?]
  (let [_ (js/console.log "kuukausi-wrapper :: vastaus" (pr-str (dissoc vastaus :sisalto)))
        kk-nyt (pvm/kuukausi (pvm/nyt))
        vuosi-nyt (pvm/vuosi (pvm/nyt))
        vastaukset (:vastaukset vastaus)
        vastaus-hyvaksytty? (if (some #(and (= kohdekuukausi (:kuukausi %))
                                          (:vastaus %)) vastaukset)
                            true false)
        vastaus-hylatty? (if (some #(and (= kohdekuukausi (:kuukausi %))
                                         (false? (:vastaus %))) vastaukset)
                           true false)
        paatos-kk? (or (= kohdekuukausi (:paatos-kk vastaus))
                       (= 0 (:paatos-kk vastaus)))
        pisteet (first (keep (fn [vastaus]
                               (when (= kohdekuukausi (:kuukausi vastaus))
                                 (:pisteet vastaus)))
                             vastaukset))
        voi-vastata? (lupaus-tiedot/voiko-vastata? kohdekuukausi vastaus)
        kk-odottaa-vastausta? (if (and (false? vastaus-hyvaksytty?) (false? vastaus-hylatty?) (nil? pisteet)
                                       voi-vastata?)
                                true false)
        ;; Kun kertakaikkiaan ei voida ottaa vastaan vastausta (ei muokata, eikä muutenkaan)
        vastausta-ei-voi-antaa? (and (false? voi-vastata?) (false? vastaus-hyvaksytty?) (false? vastaus-hylatty?))]
    [:div.col-xs-1.pallo-ja-kk (merge {:class (str (when paatos-kk? " paatoskuukausi")
                                                   (when (= kohdekuukausi vastauskuukausi) " vastaus-kk")
                                                   (when (true? voi-vastata?)
                                                     " voi-valita"))}
                                      (when (and listauksessa? voi-vastata?)
                                        {:on-click (fn [e]
                                                     (do
                                                       (.preventDefault e)
                                                       (e! (lupaus-tiedot/->AvaaLupausvastaus vastaus kohdekuukausi kohdevuosi))))}))
     (cond
       ;; Kuukausi valmis ottamaan normaalin vastauksen vastaan
       (and (true? kk-odottaa-vastausta?)
            (false? paatos-kk?)) [odottaa-vastausta kohdekuukausi]
       ;; Päätöskuukausi, jolle ei ole annettu vastausta
       (and (false? vastaus-hyvaksytty?)
            (false? vastaus-hylatty?)
            (nil? pisteet)
            (true? paatos-kk?)) [kuukaudelle-ei-paatosta-viela kohdekuukausi vastaus]
       ;; Tälle kuukaudelle ei voi antaa vastausta ollenkaan
       vastausta-ei-voi-antaa?
       [kuukaudelle-ei-voi-antaa-vastausta kohdekuukausi vastaus]
       ;; KE vastauksen kuukausi, jossa on hyväksytty tulos
       (and (true? vastaus-hyvaksytty?) (nil? pisteet)) [hyvaksytty-vastaus kohdekuukausi]
       ;; KE vastauksen kuukausi, jossa on hylätty tulos
       (true? vastaus-hylatty?) [hylatty-vastaus kohdekuukausi]
       ;; Monivalinta vastauksen kuukausi, jossa on pisteet
       (not (nil? pisteet)) [:div.kuukausi-pisteet pisteet]
       ;; Laitetaan kaikissa muissa tapauksissa tyhjä laatikko
       :else [kuukaudelle-ei-voi-antaa-vastausta nil nil])
     [kuukauden-nimi kohdekuukausi kohdevuosi kk-nyt vuosi-nyt vastausta-ei-voi-antaa?]]))