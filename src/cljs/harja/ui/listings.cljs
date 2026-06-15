(ns harja.ui.listings
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as s]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [nuolivalinta]]))

(defn rivita-pitka-teksti [s max]
  (let [ws (s/split (or s "") #"\s+")]
    (->> (reduce (fn [[line acc] w]
                   (let [cand (if (empty? line) w (str line " " w))]
                     (if (<= (count cand) max)
                       [cand acc]
                       [w (conj acc line)])))
           ["" []] ws)
      ((fn [[line acc]] (cond-> acc (seq line) (conj line)))))))

(defn suodatettu-lista
  "Luettelo, jossa on hakukenttä filtteröinnille.
  opts voi sisältää
  :term      hakutermin atomi
  :selection valitun listaitemin atomi
  :format    funktio jolla itemi muutetaan stringiksi, oletus str
  :haku      funktio jolla haetaan itemistä, kenttä jota vasten hakusuodatus (oletus :name)
  :on-select funktio, jolla valinta tehdään (oletuksena reset! valinta-atomille)
  :aputeksti
  :tunniste
  :ryhmittely     funktio jonka mukaan listan itemit ryhmitellään ja aliotsikoidaan (optionaalinen)
  :ryhman-otsikko funktio joka palauttaa otsikon ryhmittely-funktion antamalle ryhmälle
  :nayta-ryhmat   optionaalinen sekvenssi ryhmäavaimia, jonka mukaisessa järjestyksessa ryhmät
                  näytetään. Jos ei annettu, näytetään kaikki ei missään tietyssä järjestyksessä.
  :vinkki funktio, joka palauttaa vinkkitekstin hakukentän alle

  lista sisältää luettelon josta hakea."
  [opts lista]
  (let [termi-atom (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)
        haku (or (:haku opts) :name)
        on-select (or (:on-select opts) #(reset! valittu %))
        korostus-idx (atom nil)
        auki? (atom false)
        nayta-aina? (:nayta-aina? opts)]

    (fn [opts lista]
      (let [term (or (:term opts) termi-atom)
            termi @term
            itemit (filter #(s/includes? (s/lower-case (or (haku %) ""))
                              (s/lower-case (or termi "")))
                     lista)
            korostus @korostus-idx
            tunniste (if (:tunniste opts) (:tunniste opts) :id)
            ryhmitellyt-itemit (when (:ryhmittely opts) (group-by (:ryhmittely opts) itemit))
            ryhmissa? (some? ryhmitellyt-itemit)
            ryhmat (when ryhmissa?
                     (if-let [nr (:nayta-ryhmat opts)]
                       (map (juxt identity #(get ryhmitellyt-itemit %)) nr)
                       (seq ryhmitellyt-itemit)))
            kaikki-kamppeet (if ryhmissa? (mapcat second ryhmat) itemit)
            nayta? (and @auki? (or (seq kaikki-kamppeet)
                                 (when-let [v (:vinkki opts)] (v))))]

        [:div.dropdown.haku-container
         [:div.input-icon
          [:input.haku-input.form-control
           {:type "text"
            :value @term
            :placeholder (:aputeksti opts)
            :aria-label (:aria-label opts)
            :on-focus #(reset! auki? true)
            :on-blur #(js/setTimeout (fn [] (reset! auki? false)) 150)
            :on-key-down (nuolivalinta
                           #(swap! korostus-idx (fn [k] (if (or (nil? k) (= 0 k))
                                                          (dec (count kaikki-kamppeet))
                                                          (dec k))))
                           #(swap! korostus-idx (fn [k] (if (or
                                                              (nil? k)
                                                              (= (dec (count kaikki-kamppeet)) k))
                                                          0
                                                          (inc k))))
                           #(when-let [k @korostus-idx]
                              (on-select (nth kaikki-kamppeet k))
                              (reset! korostus-idx nil)))

            :on-change #(do
                          (reset! korostus-idx nil)
                          (reset! term (.-value (.-target %))))}]

          [:span.input-icon-addon {:style {:margin "auto"}}
           [:i.icon.ti.ti-search]]]

         (when (or nayta-aina? nayta?)
           [:div {:class (str "dropdown-menu dropdown-menu-card w-100" (when (or nayta-aina? nayta?) " show"))
                  :style {:maxHeight "320px" :overflowY "auto"}}
            (when-let [v (:vinkki opts)]
              (when-let [t (v)]
                (into [:<>]
                  (map-indexed (fn [i line]
                                 ^{:key i} [:div.dropdown-header line])
                    (rivita-pitka-teksti t 48)))))


            (let [selected @valittu
                  itemilista (fn [itemit alkuidx]
                               (into [:div]
                                 (map-indexed
                                   (fn [i item]
                                     ^{:key (tunniste item)}
                                     [:button.dropdown-item.w-100.text-truncate
                                      {:type "button"
                                       :data-cy "haku-lista-item"
                                       :on-mouse-down #(on-select item) ; ennen bluria
                                       :class (str (when (= item selected) " active ")
                                                (when (= (+ alkuidx i) korostus) " active "))}
                                      (fmt item)]))
                                 itemit))]
              (if ryhmissa?
                (loop [alkuidx 0 acc [] [[ryhman-nimi ryhman-kamppeet] & rs] ryhmat]
                  (if (nil? ryhman-nimi)
                    (into [:div] acc)
                    (recur (+ alkuidx (count ryhman-kamppeet))
                      (conj acc
                        ^{:key ryhman-nimi}
                        [:<>
                         [:h6.dropdown-header ((:ryhman-otsikko opts) ryhman-nimi)]
                         (itemilista ryhman-kamppeet alkuidx)])
                      rs)))
                (itemilista itemit 0)))])]))))
