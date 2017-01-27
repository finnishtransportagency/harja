(ns harja.palvelin.palvelut.interpolointi
  (:require [harja.geo :as geo]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.tilannekuva :as tilannekuva-q]
            [harja.kyselyt.konversio :as konv]))


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


(defn aika-ja-osoite-pisteessa [db klikkauspiste toteuma-id]
  (let [muunna-sijainti #(assoc % :sijainti (geo/pg->clj (:sijainti %)))
        reittipisteet (map muunna-sijainti
                           (toteumat-q/hae-toteuman-reittipisteet db toteuma-id))

        etaisyys (fn [rp]
                   (let [[x y] (-> rp :sijainti :coordinates)
                         xd (Math/abs (- x (:x klikkauspiste)))
                         yd (Math/abs (- y (:y klikkauspiste)))]
                     (Math/sqrt (+ (* xd xd) (* yd yd)))))
        lahin-piste (apply min-key etaisyys reittipisteet)
        [edellinen-piste seuraava-piste] (ymparoivat lahin-piste reittipisteet)
        _ (println "pistvec" [edellinen-piste lahin-piste seuraava-piste])
        [rp1 rp2 rp3] (map (comp clj-piste->sql :sijainti) [edellinen-piste lahin-piste seuraava-piste])
        esi-naapurit-etaisyyksilla (tilannekuva-q/reittipisteiden-sijainnit-toteuman-reitilla
                                    db {:toteuma-id toteuma-id :reittipiste-idt (map :id [edellinen-piste seuraava-piste])})
        naapurit-etaisyyksilla (map muunna-sijainti esi-naapurit-etaisyyksilla)

        lahin-naapuri (apply min-key etaisyys naapurit-etaisyyksilla)
        _ (println "tr-haku:" lahin-piste)
        osoite-vastaus (tilannekuva-q/osoite-reittipisteille db {:piste (clj-piste->sql (:sijainti lahin-piste))})
        tr-osoite (some-> osoite-vastaus first :tr_osoite .getValue konv/lue-tr-piste)
        _ (println "saatiin tr-osoite:" tr-osoite)
        paikka-vastaus (tilannekuva-q/suhteellinen-paikka-pisteiden-valissa
                        db {:rp1 (clj-piste->sql (:sijainti lahin-naapuri)) :rp2 (clj-piste->sql (:sijainti lahin-piste))
                            :piste (clj-piste->sql  {:type :point :coordinates [(:x  klikkauspiste ) (:y klikkauspiste)]})})
        pisteen-paikka (:paikka (first paikka-vastaus))
        sql->aikaleima (fn [reittipiste] (-> reittipiste :aika .getTime))
        aikaleima->aika #(new java.util.Date %)]
    [(aikaleima->aika (long (interpoloi pisteen-paikka (sql->aikaleima lahin-naapuri) (sql->aikaleima lahin-piste))))
     tr-osoite]))

(defn interpoloi-toteuman-aika-pisteelle [asia parametrit db]
  (println "itap kutsuttu, avaimet:" (keys asia))
  (let [koordinaatit (select-keys parametrit [:x :y])
        [aika tr-osoite] (aika-ja-osoite-pisteessa db koordinaatit (-> asia :tehtava :id))]
    (println "koordinaatit" koordinaatit)
    (assoc asia :aika-pisteessa aika :tierekisteriosoite tr-osoite)))

(defn interpoloi-tarkastuksen-aika-pisteelle [asia parametrit db]
  (println "itap kutsuttu, avaimet:" (keys asia))
  (let [koordinaatit (select-keys parametrit [:x :y])
        ;; [aika tr-osoite] (aika-ja-osoite-pisteessa db koordinaatit (-> asia :tehtava :id))
        ]
    (println "koordinaatit" koordinaatit)
    ;; (assoc asia :aika-pisteessa aika :tierekisteriosoite tr-osoite)
    asia
    ))
