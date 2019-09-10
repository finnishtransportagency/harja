(ns harja.ui.taulukko.osa
  "Määritellään taulukon osat täällä."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.kaytokset :as kaytokset]))

(def ^:dynamic *this* nil)

(defonce muuta-avain-teksti
         {:arvo [:teksti]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-linkki
         {:arvo [:linkki]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-ikoni
         {:arvo [:ikoni-ja-teksti]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-otsikko
         {:arvo [:otsikko]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-syote
         {:arvo [:parametrit :value]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-nappi
         {:arvo [:sisalto]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-laajenna
         {:arvo [:teksti]
          :id [:osan-id]
          :class [:parametrit :class]})
(defonce muuta-avain-komponentti
         {:arvo [:komponentin-tila]
          :id [:osan-id]})

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

(defn perus-lisaa-renderointi-derefable!
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

(defrecord Teksti [osan-id teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          tilan-seuranta-lisatty? (not (nil? renderointi))
          fmt-fn (or (::fmt this) identity)]
      (komp/luo
        {:component-did-mount (fn [this-react]
                                (when-let [aloita-seuranta (get-in this [:tilan-seuranta :seurannan-aloitus])]
                                  (aloita-seuranta)))}
        {:component-will-unmount (fn [this-react]
                                   (when-let [lopeta-seuranta (get-in this [:tilan-seuranta :seurannan-lopeuts])]
                                     (lopeta-seuranta)))}
        (fn [this]
          (let [{:keys [id class]} (:parametrit this)
                muodostettu-arvo (when tilan-seuranta-lisatty?
                                   ;; Tämä aiheuttaa re-renderöinnin, kun tila annetussa polussa muuttuu
                                   (let [muodostettu-arvo (muodosta-arvo this @renderointi)]
                                     (if (nil? muodostettu-arvo)
                                       ""
                                       muodostettu-arvo)))
                teksti (if tilan-seuranta-lisatty?
                         muodostettu-arvo
                         (:teksti this))]
            [:div.osa.osa-teksti {:class (when class
                                           (apply str (interpose " " class)))
                                  :id id
                                  :data-cy (:osan-id this)}
             (fmt-fn teksti)])))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/TilanSeuranta
  (lisaa-renderointi-derefable!
    [this tila polut alkutila]
    (perus-lisaa-renderointi-derefable! this tila polut alkutila))
  (lisaa-renderointi-derefable! [this tila polut] (p/lisaa-renderointi-derefable! this tila polut nil))
  (lisaa-muodosta-arvo [this f]
    (assoc-in this [:tilan-seuranta :muodosta-arvo]
              (fn [this renderointi]
                (f this renderointi))))
  p/Fmt
  (lisaa-fmt [this f]
    (assoc this ::fmt f))
  (lisaa-fmt-aktiiviselle [this f]
    this)
  p/Asia
  (arvo [this avain]
    (let [palautettava-arvo (get-in this (muuta-avain-teksti avain))]
      (if (= avain :arvo)
        (let [parsittu-arvo (js/Number palautettava-arvo)]
          (if (js/isNaN parsittu-arvo)
            palautettava-arvo
            parsittu-arvo))
        palautettava-arvo)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-teksti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-teksti))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-teksti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

(defrecord Linkki [osan-id linkki teksti parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          tilan-seuranta-lisatty? (not (nil? renderointi))
          fmt-fn (or (::fmt this) identity)]
      (komp/luo
        {:component-did-mount (fn [this-react]
                                (when-let [aloita-seuranta (get-in this [:tilan-seuranta :seurannan-aloitus])]
                                  (aloita-seuranta)))}
        {:component-will-unmount (fn [this-react]
                                   (when-let [lopeta-seuranta (get-in this [:tilan-seuranta :seurannan-lopeuts])]
                                     (lopeta-seuranta)))}
        (fn [this]
          (let [{:keys [id class]} (:parametrit this)
                muodostettu-arvo (when tilan-seuranta-lisatty?
                                   (let [muodostettu-arvo (muodosta-arvo this @renderointi)]
                                     (if (nil? muodostettu-arvo)
                                       ""
                                       muodostettu-arvo)))
                teksti (if tilan-seuranta-lisatty?
                         muodostettu-arvo
                         (:teksti this))]
            [:a.osa.osa-linkki {:class (when class
                                         (apply str (interpose " " class)))
                                :href linkki
                                :id id
                                :data-cy (:osan-id this)}
             (fmt-fn teksti)])))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/TilanSeuranta
  (lisaa-renderointi-derefable!
    [this tila polut alkutila]
    (perus-lisaa-renderointi-derefable! this tila polut alkutila))
  (lisaa-renderointi-derefable! [this tila polut] (p/lisaa-renderointi-derefable! this tila polut nil))
  (lisaa-muodosta-arvo [this f]
    (assoc-in this [:tilan-seuranta :muodosta-arvo]
              (fn [this renderointi]
                (f this renderointi))))
  p/Fmt
  (lisaa-fmt [this f]
    (assoc this ::fmt f))
  (lisaa-fmt-aktiiviselle [this f]
    this)
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-linkki avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-linkki))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-linkki))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-linkki avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

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
  (osan-tila [this])
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-ikoni avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-ikoni))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-ikoni))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-ikoni avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

(defrecord Otsikko [osan-id otsikko jarjesta-fn! parametrit]
  p/Osa
  (piirra-osa [this]
    (let [otsikon-jarjestys-fn! (fn [jarjesta-fn! e]
                                  (.preventDefault e)
                                  (jarjesta-fn!))
          fmt-fn (or (::fmt this) identity)]
      (fn [{:keys [otsikko parametrit osan-id]}]
        (let [{:keys [id class]} parametrit]
          [:div.osa.osa-otsikko {:class (when class
                                           (apply str (interpose " " class)))
                                  :id id
                                  :data-cy osan-id}
           (fmt-fn otsikko)
           [:span.klikattava.otsikon-jarjestys {:on-click (r/partial otsikon-jarjestys-fn! (:jarjesta-fn! this))}
            [ikonit/sort]]]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/Fmt
  (lisaa-fmt [this f]
    (assoc this ::fmt f))
  (lisaa-fmt-aktiiviselle [this f]
    this)
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-otsikko avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-otsikko))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-otsikko))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-otsikko avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

;; Syote record toimii geneerisenä input elementtinä. Jotkin toiminnot tehdään usein
;; (kuten tarkastetaan, että input on positiivinen), niin tällaiset yleiset käyttäytymiset
;; voidaan wrapata johonkin 'toiminnot' funktioon 'kayttaytymiset' parametrien avulla.
;; Käyttäytymiset määritellään eri ns:ssa.
(defrecord Syote [osan-id toiminnot kayttaytymiset parametrit]
  p/Osa
  (piirra-osa [this]
    (let [aktiivinen? (atom false)
          {:keys [on-blur on-change on-click on-focus on-input on-key-down on-key-press
                  on-key-up]} (lisaa-kaytokset (merge-with (fn [kayttajan-lisaama tassa-lisatty]
                                                                       (comp kayttajan-lisaama
                                                                             tassa-lisatty))
                                                           (:toiminnot this)
                                                           {:on-blur (fn [e]
                                                                       (reset! aktiivinen? false)
                                                                       e)
                                                            :on-focus (fn [e]
                                                                        (reset! aktiivinen? true)
                                                                        e)})
                                               (:kayttaytymiset this))
          fmt-fn (or (::fmt this) identity)
          fmt-aktiivinen-fn (or (::fmt-aktiivinen this) identity)]
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
                                        :value (if fmt-fn
                                                 (if @aktiivinen?
                                                   (fmt-aktiivinen-fn value)
                                                   (fmt-fn value))
                                                 value)
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
  (osan-tila [this])
  p/Fmt
  (lisaa-fmt [this f]
    (assoc this ::fmt f))
  (lisaa-fmt-aktiiviselle [this f]
    (assoc this ::fmt-aktiivinen f))
  p/Asia
  (arvo [this avain]
    (let [palautettava-arvo (get-in this (muuta-avain-syote avain))
          osan-tyyppi (get-in this [:parametrit :type])]
      (if (and (= avain :arvo) (or (= osan-tyyppi "text")
                                   (nil? osan-tyyppi)))
        (let [parsittu-arvo (js/Number palautettava-arvo)]
          (if (js/isNaN parsittu-arvo)
            palautettava-arvo
            parsittu-arvo))
        palautettava-arvo)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-syote))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-syote))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-syote avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

(defrecord Nappi [osan-id toiminnot kayttaytymiset sisalto parametrit]
  p/Osa
  (piirra-osa [this]
    (let [{:keys [on-blur on-change on-click on-focus on-input on-key-down on-key-press
                  on-key-up]} (lisaa-kaytokset (:toiminnot this)
                                               (:kayttaytymiset this))]
      (fn [this]
        (let [{:keys [id class type value name tabindex disabled? size]} (:parametrit this)
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
                                        :tab-index tabindex
                                        :disabled disabled?
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
          [:button.osa.osa-nappi parametrit sisalto]))))
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-nappi avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-nappi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-nappi))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-nappi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

(defrecord Laajenna [osan-id teksti aukaise-fn parametrit]
  p/Tila
  (luo-tila! [this]
    (assoc this ::tila (atom false)))
  (hae-tila [this]
    (::tila this))
  (aseta-tila! [this tila]
    (swap! (::tila this) (fn [_] tila)))
  (paivita-tila! [this f]
    (swap! (::tila this) f))
  p/Osa
  (piirra-osa [this]
    (let [auki? (or (p/hae-tila this)
                    (atom false))

          {renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          tilan-seuranta-lisatty? (not (nil? renderointi))
          fmt-fn (or (::fmt this) identity)]
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
                         ;; Tämä aiheuttaa re-renderöinnin, kun tila annetussa polussa muuttuu
                         (muodosta-arvo this @renderointi)
                         (:teksti this))]
            [:span.osa.klikattava.osa-laajenna
             {:class (when class
                       (apply str (interpose " " class)))
              :id id
              :data-cy (:osan-id this)
              :on-click
              #(do (.preventDefault %)
                   (swap! auki? not)
                   (aukaise-fn this @auki?))}
             [:span.laajenna-teksti (fmt-fn teksti)]
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
    (perus-lisaa-renderointi-derefable! this tila polut alkutila))
  (lisaa-renderointi-derefable! [this tila polut] (p/lisaa-renderointi-derefable! this tila polut nil))
  (lisaa-muodosta-arvo [this f]
    (assoc-in this [:tilan-seuranta :muodosta-arvo]
              (fn [this renderointi]
                (f this renderointi))))
  p/Fmt
  (lisaa-fmt [this f]
    (assoc this ::fmt f))
  (lisaa-fmt-aktiiviselle [this f]
    this)
  p/Asia
  (arvo [this avain]
    (let [{renderointi :atom muodosta-arvo :muodosta-arvo} (:tilan-seuranta this)
          palautettava-arvo (if (and renderointi (= avain :arvo))
                              (muodosta-arvo this @renderointi)
                              (get-in this (muuta-avain-laajenna avain)))]
      (if (= avain :arvo)
        (let [parsittu-arvo (js/Number palautettava-arvo)]
          (if (js/isNaN parsittu-arvo)
            palautettava-arvo
            parsittu-arvo))
        palautettava-arvo)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-laajenna))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-laajenna))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-laajenna avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

(defrecord Komponentti [osan-id komponentti komponentin-argumentit komponentin-tila]
  p/Osa
  (piirra-osa [this]
    [(:komponentti this) this (:komponentin-argumentit this) (:komponentin-tila this)])
  (osan-id? [this id]
    (= (:osan-id this) id))
  (osan-id [this]
    (:osan-id this))
  (osan-tila [this])
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-komponentti avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-komponentti))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-komponentti))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-komponentti avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))