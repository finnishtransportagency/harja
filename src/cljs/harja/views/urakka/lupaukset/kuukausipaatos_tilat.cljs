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
  [:div [ikonit/harja-icon-action-subtract]])

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
        paatos-kk? (or (= kohdekuukausi (:paatos-kk vastaus))
                       (= 0 (:paatos-kk vastaus)))
        pisteet (first (keep (fn [vastaus]
                               (when (= kohdekuukausi (:kuukausi vastaus))
                                 (:pisteet vastaus)))
                             vastaukset))
        ;; Kun kuukaudelle voi tehdä kirjauksen, jos se odottaa kirjausta, tai sille voidaan tehdä päätös.
        ;; Spesiaaliehtona laitetaan alkuksi sallituksi tulevaisuuteen vastaaminen.
        voi-vastata? (and ;(<= kohdevuosi vuosi-nyt)
                       ;(<= kohdekuukausi kk-nyt)
                       true
                       (or (some #(= kohdekuukausi %) (:kirjaus-kkt vastaus))
                           (= kohdekuukausi (:paatos-kk vastaus))
                           (= 0 (:paatos-kk vastaus))))
        ;_ (js/console.log "kuukausi-wrapper :: voi-vastata?" voi-vastata?)
        ;_ (js/console.log "kuukausi-wrapper :: kohdekuukausi:" (pr-str kohdekuukausi) "kohdevuosi:" (pr-str kohdevuosi) "vuosi-nyt:" (pr-str vuosi-nyt))
        kk-odottaa-vastausta? (if (and (false? vastaus-olemassa?) (false? vastaus-hylatty?) (nil? pisteet)
                                       voi-vastata?)
                                true false)]
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
       (and (false? vastaus-olemassa?)
            (false? vastaus-hylatty?)
            (true? paatos-kk?)) [kuukaudelle-ei-paatosta-viela kohdekuukausi vastaus]
       ;; Tälle kuukaudelle ei voi antaa vastausta ollenkaan
       (false? voi-vastata?)
       [kuukaudelle-ei-voi-antaa-vastausta kohdekuukausi vastaus]
       ;; KE vastauksen kuukausi, jossa on hyväksytty tulos
       (true? vastaus-olemassa?) [hyvaksytty-vastaus kohdekuukausi]
       ;; KE vastauksen kuukausi, jossa on hylätty tulos
       (true? vastaus-hylatty?) [hylatty-vastaus kohdekuukausi]
       ;; Monivalinta vastauksen kuukausi, jossa on pisteet
       (not (nil? pisteet)) [:div.kuukausi-pisteet pisteet]
       ;; Laitetaan kaikissa muissa tapauksissa tyhjä laatikko
       :else [kuukaudelle-ei-voi-antaa-vastausta nil nil])
     [kuukauden-nimi kohdekuukausi kohdevuosi kk-nyt vuosi-nyt]]))