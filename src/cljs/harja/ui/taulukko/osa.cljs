(ns harja.ui.taulukko.osa
  "Määritellään taulukon osat täällä."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.kaytokset :as kaytokset]))

(def ^:dynamic *this* nil)

(defn lisaa-kaytokset
  "Toiminnot on map, jossa avaimet vastaa input elementin saamia parametrejä. Nämä ovat siis on-change, on-key-down jne.
   Toiminnot arvot on funktio, joka saa parametrina kayttaytymisfunktion palauttaman arvon tai arvot.
   Kayttaytymiset on myös map, jossa avaimet vastaa toimintojen avaimia. Näiden avainten avulla kohdistetaan käyttäytymiset
   oikealle toiminnolle. Kayttaytymisen arvo taasen on funktioita sisältävä sequable, jonka järjestyksellä on väliä!

   Sequablen viimeinen funktio saa eventin argumentikseen. Tämän palauttama arvo annetaan toiseksi viimeiselle
   käyttäytymiselle jne. Lopuksi arvo annetaan itse toiminnolle.

   Funktioissa voi lisäksi käyttää *this* muuttujaa, jonka arvo on eventin laukaiseman osan record. Huom, *this* ei tulisi
   käyttää async kutsuissa.

   Esim.
   (let [tila-atom (atom nil)
         toiminnot {:on-change (fn [arvo] (reset! tila-atom arvo))}
         kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]}

         ;; Tässä lopputuloksena on funktio, jossa ensin oletetaan saavan javascript event (:eventin-arvo käyttäytyminen),
         ;; jonka arvo annetaan :positiivinen-numero käyttäytymiselle, joka puolestaan antaa loppputuloksen toiminnolle,
         ;; joka resetoi tila-atomin arvon.
         f-map (lisaa-kaytokset toiminnot kayttaytymiset)]
     [:input {:value @tila-atom :on-change (:on-change f-map)}])"
  [toiminnot kayttaytymiset]
  (into {}
        (map (fn [[nimi f]]
               [nimi (fn [this]
                       (fn [event]
                         (binding [*this* this]
                           (f event))))])
             (merge-with (fn [kayttaytymiset toiminto]
                           (loop [[kaytos & loput-kaytokset] kayttaytymiset
                                  lopullinen-toiminto toiminto]
                             (if (nil? kaytos)
                               lopullinen-toiminto
                               (recur loput-kaytokset
                                      (kaytokset/lisaa-kaytos kaytos lopullinen-toiminto)))))
                         kayttaytymiset
                         toiminnot))))

(defrecord Teksti [osan-id teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          tilan-seuranta-lisatty? (not (nil? renderointi))]
      (komp/luo
        {:component-did-mount (fn [this-react]
                                (when-let [aloita-seuranta (get-in this [:tilan-seuranta :seurannan-aloitus])]
                                  (aloita-seuranta)))}
        {:component-will-unmount (fn [this-react]
                                   (when-let [lopeta-seuranta (get-in this [:tilan-seuranta :seurannan-lopeuts])]
                                     (lopeta-seuranta)))}
        (fn [this]
          (let [{:keys [id class]} (:parametrit this)
                teksti (if tilan-seuranta-lisatty?
                         (muodosta-arvo this @renderointi)
                         (:teksti this))]
            (when tilan-seuranta-lisatty?
              ;; Tämä aiheuttaa re-renderöinnin, kun tila annetussa polussa muuttuu
              @renderointi)
            [:div.osa.osa-teksti {:class (when class
                                           (apply str (interpose " " class)))
                                  :id id
                                  :data-cy (:osan-id this)}
             teksti])))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/TilanSeuranta
  (lisaa-renderointi-derefable!
    [this tila polut alkutila]
    (let [renderointi (atom nil)
          watch-id (keyword (str (gensym "watch")))
          renderointi-atomin-muutos! (fn [uusi]
                                       (swap! renderointi (fn [v]
                                                            (assoc v :vanha (:uusi v)
                                                                     :uusi (zipmap polut
                                                                                   (map #(get-in uusi %) polut))))))
          seurannan-aloitus (fn []
                              (add-watch tila watch-id
                                         (fn [_ _ vanha uusi]
                                           (let [polkujen-tila-muuttunut? (not (every? true? (map (fn [polku]
                                                                                                    (= (get-in vanha polku) (get-in uusi polku)))
                                                                                                  polut)))]
                                             (when polkujen-tila-muuttunut?
                                               (renderointi-atomin-muutos! uusi))))))
          seurannan-lopetus (fn []
                              (remove-watch tila watch-id))]
      (renderointi-atomin-muutos! alkutila)
      (assoc this :tilan-seuranta
                  {:atom renderointi
                   :seurannan-aloitus seurannan-aloitus
                   :seurannan-lopeuts seurannan-lopetus})))
  (lisaa-renderointi-derefable! [this tila polut] (p/lisaa-renderointi-derefable! this tila polut nil))
  (lisaa-muodosta-arvo [this f]
    (assoc-in this [:tilan-seuranta :muodosta-arvo]
              (fn [this renderointi]
                (f this renderointi)))))

(defrecord Ikoni [osan-id ikoni-ja-teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [id class]} (:parametrit this)]
      [:div.osa.osa-ikoni {:class (when class
                                     (apply str (interpose " " class)))
                            :id id
                            :data-cy (:osan-id this)}
       [(-> this :ikoni-ja-teksti :ikoni)]
       (-> this :ikoni-ja-teksti :teksti)]))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this]))

(defrecord Otsikko [osan-id otsikko jarjesta-fn! parametrit]
  p/Osa
  (piirra-osa [_]
    (let [otsikon-jarjestys-fn! (fn [jarjesta-fn! e]
                                  (.preventDefault e)
                                  (jarjesta-fn!))]
      (fn [this]
        (let [{:keys [id class]} (:parametrit this)]
          [:div.osa.osa-otsikko {:class (when class
                                           (apply str (interpose " " class)))
                                  :id id
                                  :data-cy (:osan-id this)}
           (:otsikko this)
           [:span.klikattava.otsikon-jarjestys {:on-click (r/partial otsikon-jarjestys-fn! (:jarjesta-fn! this))}
            [ikonit/sort]]]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this]))

;; Syote record toimii geneerisenä input elementtinä. Jotkin toiminnot tehdään usein
;; (kuten tarkastetaan, että input on positiivinen), niin tällaiset yleiset käyttäytymiset
;; voidaan wrapata johonkin 'toiminnot' funktioon 'kayttaytymiset' parametrien avulla.
;; Käyttäytymiset määritellään eri ns:ssa.
(defrecord Syote [osan-id toiminnot kayttaytymiset parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [on-blur on-change on-click on-focus on-input on-key-down on-key-press
                  on-key-up]} (lisaa-kaytokset (:toiminnot this) (:kayttaytymiset this))]
      (fn [this]
        (let [{:keys [id class type value name readonly? required? tabindex disabled?
                      checked? default-checked? indeterminate?
                      alt height src width
                      autocomplete max max-length min min-length pattern placeholder size]} (:parametrit this)
              parametrit (into {}
                               (remove (fn [[_ arvo]]
                                         (nil? arvo))
                                       {;; Inputin parametrit
                                        :class (when class
                                                 (apply str (interpose " " class)))
                                        :data-cy (:osan-id this)
                                        :id id
                                        :type type
                                        :value value
                                        :name name
                                        :read-only readonly?
                                        :required required?
                                        :tab-index tabindex
                                        :disabled disabled?
                                        ;; checkbox or radio paramterit
                                        :checked checked?
                                        :default-checked default-checked?
                                        :indeterminate indeterminate?
                                        ;; kuvan parametrit
                                        :alt alt
                                        :height height
                                        :src src
                                        :width width
                                        ;; numero/teksti input
                                        :auto-complete autocomplete
                                        :max max
                                        :max-length max-length
                                        :min min
                                        :min-length min-length
                                        :pattern pattern
                                        :placeholder placeholder
                                        :size size
                                        ;; GlobalEventHandlers
                                        :on-blur (when on-blur
                                                   (on-blur this))
                                        :on-change (when on-change
                                                     (on-change this))
                                        :on-click (when on-click
                                                    (on-click this))
                                        :on-focus (when on-focus
                                                    (on-focus this))
                                        :on-input (when on-input
                                                    (on-input this))
                                        :on-key-down (when on-key-down
                                                       (on-key-down this))
                                        :on-key-press (when on-key-press
                                                        (on-key-press this))
                                        :on-key-up (when on-key-up
                                                     (on-key-up this))}))]
          [:input.osa.osa-syote parametrit]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this]))

(defrecord Laajenna [osan-id teksti aukaise-fn parametrit]
  p/Tila
  (hae-tila [this]
    (:tila this))
  (aseta-tila! [this]
    (assoc this :tila (atom false)))
  p/Osa
  (piirra-osa [this]
    (let [auki? (or (p/hae-tila this)
                    (atom false))

          {renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          tilan-seuranta-lisatty? (not (nil? renderointi))]
      (komp/luo
        {:component-did-mount (fn [this-react]
                                (when-let [aloita-seuranta (get-in this [:tilan-seuranta :seurannan-aloitus])]
                                  (aloita-seuranta)))}
        {:component-will-unmount (fn [this-react]
                                   (when-let [lopeta-seuranta (get-in this [:tilan-seuranta :seurannan-lopeuts])]
                                     (lopeta-seuranta)))}
        (fn [this]
          (let [{:keys [id class ikoni]} (:parametrit this)
                ikoni (or ikoni "chevron")
                ikoni-auki (if (= ikoni "chevron")
                             ikonit/livicon-chevron-down
                             ikonit/triangle-bottom)
                ikoni-kiinni (if (= ikoni "chevron")
                               ikonit/livicon-chevron-up
                               ikonit/triangle-top)
                teksti (if tilan-seuranta-lisatty?
                         (muodosta-arvo this @renderointi)
                         (:teksti this))]
            (when tilan-seuranta-lisatty?
              ;; Tämä aiheuttaa re-renderöinnin, kun tila annetussa polussa muuttuu
              @renderointi)
            [:span.osa.klikattava.osa-laajenna
             {:class (when class
                       (apply str (interpose " " class)))
              :id id
              :data-cy (:osan-id this)
              :on-click
              #(do (.preventDefault %)
                   (swap! auki? not)
                   (aukaise-fn this @auki?))}
             [:span.laajenna-teksti teksti]
             (if @auki?
               ^{:key "laajenna-auki"}
               [ikoni-auki]
               ^{:key "laajenna-kiini"}
               [ikoni-kiinni])])))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this]
    (p/hae-tila this))
  p/TilanSeuranta
  (lisaa-renderointi-derefable!
    [this tila polut alkutila]
    (let [renderointi (atom nil)
          watch-id (keyword (str (gensym "watch")))
          renderointi-atomin-muutos! (fn [uusi]
                                       (swap! renderointi (fn [v]
                                                            (assoc v :vanha (:uusi v)
                                                                     :uusi (zipmap polut
                                                                                   (map #(get-in uusi %) polut))))))
          seurannan-aloitus (fn []
                              (add-watch tila watch-id
                                         (fn [_ _ vanha uusi]
                                           (let [polkujen-tila-muuttunut? (not (every? true? (map (fn [polku]
                                                                                                    (= (get-in vanha polku) (get-in uusi polku)))
                                                                                                  polut)))]
                                             (when polkujen-tila-muuttunut?
                                               (renderointi-atomin-muutos! uusi))))))
          seurannan-lopetus (fn []
                              (remove-watch tila watch-id))]
      (renderointi-atomin-muutos! alkutila)
      (assoc this :tilan-seuranta
                  {:atom renderointi
                   :seurannan-aloitus seurannan-aloitus
                   :seurannan-lopeuts seurannan-lopetus})))
  (lisaa-renderointi-derefable! [this tila polut] (p/lisaa-renderointi-derefable! this tila polut nil))
  (lisaa-muodosta-arvo [this f]
    (assoc-in this [:tilan-seuranta :muodosta-arvo]
              (fn [this renderointi]
                (f this renderointi)))))

(defrecord Komponentti [osan-id komponentti komponentin-argumentit komponentin-tila]
  p/Osa
  (piirra-osa [this]
    [(:komponentti this) this (:komponentin-argumentit this) (:komponentin-tila this)])
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this]))

(defn luo-tilallinen-laajenna [osan-id teksti aukaise-fn parametrit]
  (p/aseta-tila! (->Laajenna osan-id teksti aukaise-fn parametrit)))