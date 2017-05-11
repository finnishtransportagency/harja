(ns harja.views.kartta.infopaneeli
  "Muodostaa kartalle overlayn, joka sisältää klikatussa koordinaatissa
  olevien asioiden tiedot."
  (:require [harja.ui.komponentti :as komp]
            [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! error] :as log]
            [harja.ui.yleiset :refer [ajax-loader-pieni ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.debug :refer [debug]]
            [harja.ui.kentat :as kentat]
            [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]))

(defn otsikko
  "Näyttää infopaneelin asialle otsikon, jota klikkaamalla asian saa auki/kiinni"
  [{:keys [otsikko] :as asia} toggle-asia!]
  [:div.ip-osio
   [:div.ip-otsikko
    {:on-click toggle-asia!}
    [:span.ip-haitari-otsikko.klikattava otsikko]]])

(defn- kentan-arvo [skeema data]
  (let [arvo-fn (or (:hae skeema) (:nimi skeema))
        arvo (when arvo-fn (arvo-fn data))]
    ;; Kentat namespace olettaa, että kentän arvo tulee atomissa
    (when arvo
      (r/wrap arvo
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
     (when-let [linkit (tyyppi linkin-kasittelijat)]
       [:div
        (doall
         (map-indexed
          (fn [i {:keys [teksti ikoni tooltip toiminto]}]
            ^{:key (str "ip-toiminto-" i)}
            [yleiset/wrap-if tooltip
             [yleiset/tooltip {} :% tooltip]
             [napit/yleinen teksti #(toiminto data) {:ikoni ikoni
                                                     :luokka "ip-toiminto btn-xs"}]])
          (if (vector? linkit)
            linkit [linkit])))])
     (apply yleiset/tietoja {}
            (mapcat (juxt :otsikko
                          (fn [kentan-skeema]
                            (when-let [arvo (kentan-arvo kentan-skeema data)]
                              [kentat/nayta-arvo kentan-skeema arvo])))
                    tiedot))]))

(defn sulje-nappi [piilota-fn!]
  (when piilota-fn!
    [:div
     [napit/sulje-ruksi piilota-fn!]]))

(defn infopaneeli-komponentti [{:keys [haetaan? avatut-asiat toggle-asia! piilota-fn! linkkifunktiot]} asiat]
  [:span
   [sulje-nappi piilota-fn!]
   (when haetaan? [ajax-loader-pieni "Haetaan..." {:luokka "ip-loader"}])

   (when (and (empty? asiat) (not haetaan?))
     [:span "Pisteestä ei löytynyt hakutuloksia."])

   (doall
    (for [[i asia] (partition 2 (interleave (range) asiat))
          :let [auki? (avatut-asiat asia)]]
      ^{:key (str "infopaneelin-elementti_" i)}
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
      (komp/vanhat-ja-uudet-parametrit
        (fn [[vanhat-asiat _ _] [uudet-asiat _ _]]
          ;; Infopaneeli saa propseja joka kerta kun karttaa zoomataa, jonka takia avatut asiat
          ;; resetoitiin joka kerta kun vaikka nyt esimerkiksi zoomasi.
          (when-not (= vanhat-asiat uudet-asiat) (paivita-asiat! uudet-asiat))))
     (fn [{haetaan? :haetaan? :as asiat-pisteessa} piilota-fn! linkkifunktiot]
       [infopaneeli-komponentti
        {:haetaan? haetaan?
         :avatut-asiat @avatut-asiat
         :toggle-asia! toggle-asia!
         :piilota-fn! piilota-fn!
         :linkkifunktiot @linkkifunktiot}
        @asiat-skeemamuodossa]))))
