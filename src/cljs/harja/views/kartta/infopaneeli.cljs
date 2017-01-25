(ns harja.views.kartta.infopaneeli
  "Muodostaa kartalle overlayn, joka sisältää klikatussa koordinaatissa
  olevien asioiden tiedot."
  (:require [harja.ui.komponentti :as komp]
            [reagent.core :refer [atom] :as r]
            [cljs.core.async :as async]
            [harja.loki :refer [log tarkkaile! error] :as log]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.debug :refer [debug]]
            [harja.ui.kentat :as kentat]
            [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]
            [harja.ui.ikonit :as ikonit])
  (:require-macros
   [cljs.core.async.macros :as async-macros]))

(defn otsikko
  "Näyttää infopaneelin asialle otsikon, jota klikkaamalla asian saa auki/kiinni"
  [{:keys [otsikko] :as asia} toggle-asia!]
  [:div.ip-osio
   [:div.ip-otsikko
    {:on-click toggle-asia!}
    [:span.ip-haitari-otsikko.klikattava otsikko]]])

(defn- kentan-arvo [skeema data]
  (let [arvo-fn (or (:hae skeema) (:nimi skeema))]
    ;; Kentat namespace olettaa, että kentän arvo tulee atomissa
    (when arvo-fn
      (r/wrap (arvo-fn data)
              #(log/error "Infopaneelissa ei voi muokata tietoja: " %)))))

(defn yksityiskohdat
  "Näyttää infopaneelin asian yksityiskohdat otsikko/arvo riveinä."
  [{:keys [otsikko tiedot data tyyppi] :as asia} linkin-kasittelijat]
  (if-not (or (keyword? tyyppi) (fn? tyyppi))
    (do
      (error "yksityiskohdat: asian tyyppi huono, asia: " (clj->js asia))
      nil)
    ;; else
    [:div.ip-osio
     (when-let [{:keys [teksti toiminto]} (tyyppi linkin-kasittelijat)]
       [:div [napit/yleinen teksti #(toiminto data) {:luokka "ip-toiminto btn-xs"}]])
     (apply yleiset/tietoja {}
            (mapcat (juxt :otsikko
                          (fn [kentan-skeema]
                            [kentat/nayta-arvo kentan-skeema (kentan-arvo kentan-skeema data)]))
                    tiedot))]))

(defn sulje-nappi [piilota-fn!]
  (when piilota-fn!
    [:div
     [napit/sulje piilota-fn!]]))

(defn infopaneeli-komponentti [{:keys [avatut-asiat toggle-asia! piilota-fn! linkkifunktiot
                                       ei-tuloksia]} asiat]
  [:span
   [sulje-nappi piilota-fn!]

   (when (and (empty? asiat) ei-tuloksia)
     ei-tuloksia)

   (doall
    (for [[i asia] (zipmap (range) asiat)
          :let [auki? (avatut-asiat asia)]]
      ^{:key i}
      [:div
       [otsikko asia #(toggle-asia! asia)]
       (when auki?
         [yksityiskohdat asia linkkifunktiot])]))])

(defn infopaneeli [asiat-pisteessa piilota-fn! linkkifunktiot]
  (let [asiat-skeemamuodossa (atom nil)
        avatut-asiat (atom #{})
        toggle-asia! (fn [asia]
                       (swap! avatut-asiat
                              #(if (% asia)
                                 (disj % asia)
                                 (conj % asia))))
        paivita-asiat! (fn [{asiat :asiat :as asiat-pisteessa}]
                         (let [asiat (infopaneelin-sisalto/skeemamuodossa asiat)]
                           (reset! asiat-skeemamuodossa asiat)
                           (reset! avatut-asiat
                                   (if (= 1 (count asiat))
                                     (into #{} asiat)
                                     #{}))))]
    (paivita-asiat! asiat-pisteessa)
    (komp/luo
     (komp/kun-muuttuu (fn [asiat-pisteessa _ _]
                         (paivita-asiat! asiat-pisteessa)))
     (fn [{haetaan? :haetaan? :as asiat-pisteessa} piilota-fn! linkkifunktiot]
       (if haetaan?
         [:div
          [sulje-nappi piilota-fn!]
          [ajax-loader]]
         [infopaneeli-komponentti
          {:avatut-asiat @avatut-asiat
           :toggle-asia! toggle-asia!
           :piilota-fn! piilota-fn!
           :linkkifunktiot @linkkifunktiot}
          @asiat-skeemamuodossa])))))
