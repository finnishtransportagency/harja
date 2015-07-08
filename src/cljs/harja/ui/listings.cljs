(ns harja.ui.listings
  (:require [reagent.core :as reagent :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [nuolivalinta]]))


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
  (let [term (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)

        ;; Itemin hakukenttä, oletuksena :name
        haku (or (:haku opts) :name)

        ;; Jos valinnan tekemiseen on määritelty funktio, käytä sitä. Muuten reset! valinta atomille.
        on-select (or (:on-select opts) #(reset! valittu %))

        ;; Indeksi korostettuun elementtiin näppäimistöliikkumista varten (nil jos ei korostettua)
        korostus-idx (atom nil)


        ]
    (fn [opts lista]

      (let [termi @term
            itemit (filter #(not= (.indexOf (.toLowerCase (haku %)) (.toLowerCase termi)) -1) lista)
            
            korostus @korostus-idx
            tunniste (if (:tunniste opts)
                       (:tunniste opts)
                       :id)

            ryhmitellyt-itemit (when (:ryhmittely opts)
                                 (group-by (:ryhmittely opts) itemit))
            ryhmissa? (not (nil? ryhmitellyt-itemit))
            ryhmat (when ryhmissa?
                     (if-let [nr (:nayta-ryhmat opts)]
                       (map (juxt identity #(get ryhmitellyt-itemit %)) nr)
                       (seq ryhmitellyt-itemit)))

            kaikki-kamppeet (if ryhmissa?
                              (mapcat second ryhmat)
                              itemit)]
        [:div.haku-container
         [:input.haku-input.form-control
          {:type        "text"
           :value       @term
           :placeholder (:aputeksti opts)

           ;; käsitellään ylos/alas/enter näppäimet, joilla listasta voi valita näppäimistöllä
           :on-key-down (nuolivalinta
                         ;; Ylös
                         #(swap! korostus-idx (fn [k]
                                                (if (or (nil? k)
                                                        (= 0 k))
                                                  (dec (count kaikki-kamppeet))
                                                  (dec k))))
                         ;; Alas
                         #(swap! korostus-idx (fn [k]
                                                (if (or (nil? k)
                                                        (= (dec (count kaikki-kamppeet)) k))
                                                  0
                                                  (inc k))))

                         ;; Enter
                         #(when-let [k @korostus-idx]
                            (on-select (nth kaikki-kamppeet k))
                            (reset! korostus-idx nil)))
           :on-change   #(do
                           (reset! korostus-idx nil)
                           (reset! term (.-value (.-target %)))
                           (.log js/console (-> % .-target .-value)))}]
         [:div.haku-lista-container
          (when-let [vinkki-fn (:vinkki opts)]
            (when (vinkki-fn) [:div.haku-vinkki (vinkki-fn)]))
          (when-not (empty? lista)

             (let [selected @valittu
                   
                   itemilista (fn [itemit alkuidx]
                                [:ul.haku-lista
                                (map-indexed
                                  (fn [i item]
                                     ^{:key (tunniste item)}
                                     [:li.haku-lista-item.klikattava
                                      {:on-click #(on-select item)
                                       :class    (str (when (= item selected) "selected ")
                                                      (when (= (+ alkuidx i) korostus) "korostettu "))}
                                      [:div.haku-lista-item-nimi
                                       (fmt item)]])
                                  itemit)])]
               (if ryhmissa?
                 (loop [alkuidx 0
                        acc nil
                        [[ryhman-nimi ryhman-kamppeet] & ryhmat] ryhmat]
                   (if (nil? ryhman-nimi)
                     (reverse acc)
                     (recur (+ alkuidx (count ryhman-kamppeet))
                            (conj acc
                                  ^{:key ryhman-nimi}
                                  [:div.haku-lista-ryhma
                                   [:div.haku-lista-ryhman-otsikko ((:ryhman-otsikko opts) ryhman-nimi)]
                                   (itemilista ryhman-kamppeet alkuidx)])
                            ryhmat)))
                 (itemilista itemit 0))))]]))))
