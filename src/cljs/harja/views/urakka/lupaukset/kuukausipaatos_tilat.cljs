(ns harja.views.urakka.lupaukset.kuukausipaatos-tilat
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.lupaukset :as lupaus-tiedot]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]))

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
  [:div.kk-nimi {:class (str (when (and (false? ei-voi-vastata?) kuluva-kuukausi?) " lihavoitu")
                             (when ei-voi-vastata? " himmennetty"))} (pvm/kuukauden-lyhyt-nimi kk)])

(defn kuukaudelle-ei-voi-antaa-vastausta []
  [:div (gstring/unescapeEntities "&nbsp;")])

(defn odottaa-vastausta []
  [:div {:style {:color "#FFC300"}} [ikonit/harja-icon-status-help]])

(defn hyvaksytty-vastaus []
  [:div {:style {:color "#1C891C"}} [ikonit/harja-icon-status-selected]])

(defn hylatty-vastaus []
  [:div {:style {:color "#B40A14"}} [ikonit/harja-icon-status-denied]])

(defn kuukausi-wrapper [e! kohdekuukausi kohdevuosi vastaus vastauskuukausi listauksessa? nayta-valittu?]
  (let [kk-nyt (pvm/kuukausi (pvm/nyt))
        vuosi-nyt (pvm/vuosi (pvm/nyt))
        tulevaisuudessa? (or (> kohdevuosi vuosi-nyt)
                             (and (= kohdevuosi vuosi-nyt) (> kohdekuukausi kk-nyt)))
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
        ;; Kun kertakaikkiaan ei voida ottaa vastaan vastausta (ei muokata, ei ole päättävä kuukausi)
        vastausta-ei-voi-antaa? (and (false? voi-vastata?) (false? vastaus-hyvaksytty?) (false? vastaus-hylatty?) (false? paatos-kk?))]
    [:div.col-xs-1.pallo-ja-kk (merge {:class (str (when paatos-kk? " paatoskuukausi")
                                                   (when (and (= kohdekuukausi vastauskuukausi) nayta-valittu?) " vastaus-kk")
                                                   (when (true? voi-vastata?)
                                                     " voi-valita"))}
                                      (when (and listauksessa? voi-vastata?)
                                        {:on-click (fn [e]
                                                     (do
                                                       (.preventDefault e)
                                                       (e! (lupaus-tiedot/->AvaaLupausvastaus vastaus kohdekuukausi kohdevuosi))))}))
     (cond
       ;; Kuukausi valmis ottamaan normaalin vastauksen vastaan kuluvalle kuukaudelle tai menneisyyteen
       (and (true? kk-odottaa-vastausta?)
            (false? paatos-kk?))
       [odottaa-vastausta tulevaisuudessa?]

       ;; Päätöskuukausi, jolle ei ole annettu vastausta
       (and (false? vastaus-hyvaksytty?)
            (false? vastaus-hylatty?)
            (nil? pisteet)
            (true? paatos-kk?))
       [odottaa-vastausta tulevaisuudessa?]

       ;; Tälle kuukaudelle ei voi antaa vastausta ollenkaan
       vastausta-ei-voi-antaa?
       [kuukaudelle-ei-voi-antaa-vastausta]

       ;; KE vastauksen kuukausi, jossa on hyväksytty tulos
       (and (true? vastaus-hyvaksytty?) (nil? pisteet))
       [hyvaksytty-vastaus]

       ;; KE vastauksen kuukausi, jossa on hylätty tulos
       (true? vastaus-hylatty?)
       [hylatty-vastaus]

       ;; Monivalinta vastauksen kuukausi, jossa on pisteet
       (not (nil? pisteet))
       [:div.kuukausi-pisteet pisteet]

       ;; Laitetaan kaikissa muissa tapauksissa tyhjä laatikko
       :else
       [kuukaudelle-ei-voi-antaa-vastausta])

     [kuukauden-nimi kohdekuukausi kohdevuosi kk-nyt vuosi-nyt vastausta-ei-voi-antaa?]]))

(defn kuukausi-wrapper2 [e!
                         lupaus
                         {:keys [kuukausi vuosi odottaa-kannanottoa? paattava-kuukausi? kirjauskuukausi? vastaus] :as lupaus-kuukausi}
                         listauksessa? nayta-valittu?]
  (let [vastausta-ei-voi-antaa? (and (false? paattava-kuukausi?) (false? kirjauskuukausi?))
        voi-vastata? (and (not vastausta-ei-voi-antaa?)
                          (or kirjauskuukausi? (roolit/tilaajan-kayttaja? @istunto/kayttaja)))]
    [:div.col-xs-1.pallo-ja-kk (merge {:class (str (when paattava-kuukausi? " paatoskuukausi")
                                                   ;; TODO
                                                   (when (and #_(= kohdekuukausi vastauskuukausi) nayta-valittu?) " vastaus-kk")
                                                   (when (true? voi-vastata?)
                                                     " voi-valita"))}
                                      (when (and listauksessa? voi-vastata?)
                                        {:on-click (fn [e]
                                                     (do
                                                       (.preventDefault e)
                                                       (e! (lupaus-tiedot/->AvaaLupausvastaus lupaus kuukausi vuosi))))}))
     (cond
       odottaa-kannanottoa?
       [odottaa-vastausta]

       ;; Tälle kuukaudelle ei voi antaa vastausta ollenkaan
       vastausta-ei-voi-antaa?
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

       ;; Laitetaan kaikissa muissa tapauksissa tyhjä laatikko
       :else
       [kuukaudelle-ei-voi-antaa-vastausta])

     [kuukauden-nimi kuukausi (= :kuluva-kuukausi (:nykyhetkeen-verrattuna lupaus-kuukausi)) vastausta-ei-voi-antaa?]]))