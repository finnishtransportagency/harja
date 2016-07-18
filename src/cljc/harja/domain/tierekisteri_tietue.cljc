(ns harja.domain.tierekisteri-tietue
  (:require [clojure.string :as str]
    #?@(:cljs [[harja.loki :refer [log]]])
            [harja.tyokalut.merkkijono :as merkkijono]))

(defn jarjesta-ja-suodata-tietolajin-kuvaus [tietolajin-kuvaus]
  (sort-by :jarjestysnumero (filter :jarjestysnumero (:ominaisuudet tietolajin-kuvaus))))

(defn heita-poikkeus [tietolaji virhe]
  (let [viesti (str "Virhe tietolajin " tietolaji " arvojen käsittelyssä: " virhe)]
    (throw (Exception. viesti))))

(defn validoi-arvo [tietolaji {:keys [kenttatunniste pakollinen pituus]} arvo]
  (when (and pakollinen (not arvo))
    (heita-poikkeus tietolaji (str "Pakollinen arvo puuttuu kentästä: " kenttatunniste)))
  (when (< pituus (count arvo))
    (heita-poikkeus tietolaji (str "Liian pitkä arvo kentässä: " kenttatunniste " maksimipituus: " pituus))))

(defn hae-arvo
  "Ottaa arvot stringinä ja etsii sieltä halutun arvon käyttäen apuna kenttien-kuvaukset -mappia."
  [arvot-merkkijono kenttien-kuvaukset jarjestysnumero]
  (let [jarjestysnumeron-kentta (first (filter #(= (:jarjestysnumero %) jarjestysnumero)
                                         kenttien-kuvaukset))
        alkuindeksi (apply +
                           (map :pituus
                                (filter #(< (:jarjestysnumero %) jarjestysnumero)
                                        kenttien-kuvaukset)))
        loppuindeksi (+ alkuindeksi (:pituus jarjestysnumeron-kentta))
        teksti (clojure.string/trim (subs arvot-merkkijono alkuindeksi loppuindeksi))]
    ;; todo: tarviiko castata tietotyypin mukaan?
    teksti))


;; TODO Tätä ei kai tarvitse koska skeemassa ei ole enää avain arvo -mappeja vaan yksi mappi jossa kaikki
#_(defn muodosta-kentta [tietolaji {:keys [pituus kenttatunniste] :as kentan-kuvaus} arvot]
    (let [arvo (:arvo (first (filter #(= kenttatunniste (:avain %)) arvot)))]
      (validoi-arvo tietolaji kentan-kuvaus arvo)
      (merkkijono/tayta-oikealle pituus arvo)))


;; TODO Tätä ei kai tarvitse koska skeemassa ei ole enää avain arvo -mappeja vaan yksi mappi jossa kaikki
#_(defn pura-kentta [tietolaji kenttien-kuvaukset
                   {:keys [jarjestysnumero kenttatunniste] :as kentan-kuvaus}
                   arvot-merkkijono]
  (let [arvo (hae-arvo arvot-merkkijono kenttien-kuvaukset jarjestysnumero)]
    (validoi-arvo tietolaji kentan-kuvaus arvo)
    {:avain kenttatunniste :arvo arvo}))

;; TODO Tätä ei kai tarvitse koska skeemassa ei ole enää avain arvo -mappeja vaan yksi mappi jossa kaikki
#_(defn muodosta-arvot [tietolajin-kuvaus arvot]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kentat (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)]
    (str/join (mapv #(muodosta-kentta tietolaji % arvot) kentat))))

;; TODO Tätä ei kai tarvitse koska skeemassa ei ole enää avain arvo -mappeja vaan yksi mappi jossa kaikki
#_(defn pura-arvot [tietolajin-kuvaus arvot]
  (let [tietolaji (:tunniste tietolajin-kuvaus)
        kentat (jarjesta-ja-suodata-tietolajin-kuvaus tietolajin-kuvaus)]
    (mapv #(pura-kentta tietolaji kentat % arvot) kentat)))


