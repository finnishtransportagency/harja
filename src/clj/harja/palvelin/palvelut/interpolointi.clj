(ns harja.palvelin.palvelut.interpolointi
  (:require [harja.geo :as geo]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.tilannekuva :as tilannekuva-q]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]))


(defn- clj-piste->sql [m]
  (assert (contains? m :coordinates) m)
  (let [[x y] (:coordinates m)]
    (str "POINT(" (float x) " " (float y) ")")))

(defn- interpoloi [n alkuarvo loppuarvo]
  (+ alkuarvo (* (- loppuarvo alkuarvo) n)))

(defn- ymparoivat [piste reittipisteet]
  (let [pisteen-gettime #(-> % :aika .getTime)
        myohemmat-pisteet (filter #(> (pisteen-gettime %) (pisteen-gettime piste)) reittipisteet)
        aiemmat-pisteet (filter #(< (pisteen-gettime %) (pisteen-gettime piste)) reittipisteet)
        lahin-aiempi (if (not-empty aiemmat-pisteet)
                       (apply max-key pisteen-gettime aiemmat-pisteet)
                       piste)
        lahin-myohempi (if (not-empty myohemmat-pisteet)
                         (apply min-key pisteen-gettime myohemmat-pisteet)
                         piste)]
    [lahin-aiempi lahin-myohempi]))


(defn- tr-osoite-pisteelle [db geo-piste etaisyys]
  (let [osoite-vastaus (tilannekuva-q/osoite-reittipisteille db {:piste (clj-piste->sql (:sijainti geo-piste)) :etaisyys etaisyys})]
    (some-> osoite-vastaus first :tr_osoite .getValue konv/lue-tr-piste)))

(defn- muunna-sijainti [geo-piste] (assoc geo-piste :sijainti (geo/pg->clj (:sijainti geo-piste))))

(defn- aika-ja-osoite-pisteessa [db klikkauspiste toteuma-id]
  (if-let [reittipisteet (not-empty (map muunna-sijainti
                                           (toteumat-q/hae-toteuman-reittipisteet db toteuma-id)))]
    (let [etaisyys (fn [rp]
                     (let [[x y] (-> rp :sijainti :coordinates)
                           xd (Math/abs (- x (:x klikkauspiste)))
                           yd (Math/abs (- y (:y klikkauspiste)))]
                       (Math/sqrt (+ (* xd xd) (* yd yd)))))
          lahin-piste (apply min-key etaisyys reittipisteet)
          [edellinen-piste seuraava-piste] (ymparoivat lahin-piste reittipisteet)
          [rp1 rp2 rp3] (map (comp clj-piste->sql :sijainti) [edellinen-piste lahin-piste seuraava-piste])
          esi-naapurit-etaisyyksilla (tilannekuva-q/reittipisteiden-sijainnit-toteuman-reitilla
                                      db {:toteuma-id toteuma-id :reittipiste-idt (map :id [edellinen-piste seuraava-piste])})
          naapurit-etaisyyksilla (map muunna-sijainti esi-naapurit-etaisyyksilla)

          lahin-naapuri (apply min-key etaisyys naapurit-etaisyyksilla)

          paikka-vastaus (tilannekuva-q/suhteellinen-paikka-pisteiden-valissa
                          db {:rp1 (clj-piste->sql (:sijainti lahin-naapuri)) :rp2 (clj-piste->sql (:sijainti lahin-piste))
                              :piste (clj-piste->sql  {:type :point :coordinates [(:x  klikkauspiste ) (:y klikkauspiste)]})})
          pisteen-paikka (:paikka (first paikka-vastaus))
          sql->aikaleima (fn [reittipiste] (-> reittipiste :aika .getTime))
          aikaleima->aika #(new java.util.Date %)]
      [(aikaleima->aika (long (interpoloi pisteen-paikka (sql->aikaleima lahin-naapuri) (sql->aikaleima lahin-piste))))
       (tr-osoite-pisteelle db lahin-piste 30)])
    [nil nil]))

(defn interpoloi-toteuman-aika-pisteelle [asia parametrit db]
  (let [koordinaatit (select-keys parametrit [:x :y])
        [aika tr-osoite] (aika-ja-osoite-pisteessa db koordinaatit (-> asia :tehtava :id))]
    ;; jätetään entinen tierekisteriosoite-avain sikseen jos reittipisteitä ei löydy.
    (assoc asia :aika-pisteessa aika :tierekisteriosoite (or tr-osoite (:tierekisteriosoite asia)))))
