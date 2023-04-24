(ns harja.palvelin.palvelut.debug
  "Erinäisiä vain JVH:lle tarkoitettuja palveluita, joilla voi selvitellä
  eri tilanteita, esim. TR-osiossa."
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [harja.kyselyt.debug :as q]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.reittitoteuma :as reittitoteuma]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.geo :as geo]))

(defn hae-toteuman-reitti-ja-pisteet [db toteuma-id]
  (let [tulos (konv/sarakkeet-vektoriin
               (map konv/alaviiva->rakenne
                    (q/hae-toteuman-reitti-ja-pisteet
                     db {:toteuma-id toteuma-id}))
               {:reittipiste :reittipisteet})]
    {:reitti (:reitti (first tulos))
     :reittipisteet (:reittipisteet (first tulos))}))

(defn hae-tyokonehavainto-reitti [db params]
  (let [tulos (q/hae-tyokonehavainto-reitti db {:tyokoneid (:tyokone-id params)})
        reitti (:sijainti (first tulos))]
    reitti))
(defn hae-seuraava-vapaa-ulkoinen-id [db params]
  (let [tulos (q/seuraava-vapaa-ulkoinen-id db)
        _ (println "hae-seuraava-vapaa-ulkoinen-id :: tulos: " tulos)]
    (:ulkoinen_id (first tulos))))

(defn hae-urakan-tierekisteriosoitteita [db params]
  (let [tulos (q/hae-urakan-tierekisteriosoitteita db {:urakka-id (:urakka-id params)})
        _ (println "hae-urakan-tierekisteriosoitteita :: tulos: " tulos)]
    tulos))

(defn paivita-raportit [db params]
  (let [_ (q/paivita-toteuma-tehtavat db)
        _ (q/paivita-toteuma-materiaalit db)
        _ (q/paivita-pohjavesialuekooste db)
        _ (q/paivita-pohjavesialueiden-suolatoteumat db)
        _ (q/paivita-materiaalin-kaytto-urakalle db params)]))

(defn geometrisoi-reittoteuma [db json]
  (let [parsittu  (cheshire/decode json)
        reitti (or (get-in parsittu ["reittitoteuma" "reitti"])
                   (get-in parsittu ["reittitoteumat" 0 "reittitoteuma" "reitti"]))
        pisteet (mapv (fn [{{koordinaatit "koordinaatit"
                             aika "aika"} "reittipiste"}]
                        [(get koordinaatit "x") (get koordinaatit "y") aika])
                      reitti)]
    (reittitoteuma/hae-reitti db pisteet)))

(defn geometrisoi-tarkastus [db json]
  (let [tarkastukset (get-in (cheshire/decode json) ["tarkastukset"])
        geometriat (mapv (fn [{tarkastus "tarkastus"}]
                        (let [alkusijainti (clojure.walk/keywordize-keys (get-in tarkastus ["alkusijainti"]))
                              loppusijainti (clojure.walk/keywordize-keys (get-in tarkastus ["loppusijainti"]))
                              tr-osoite (sijainnit/hae-tierekisteriosoite db alkusijainti loppusijainti)
                              pisteet-alku (tierekisteri-haku/hae-tr-pisteella db alkusijainti)
                              pisteet-loppu (tierekisteri-haku/hae-tr-pisteella db loppusijainti)
                              geometria (if tr-osoite
                                          (:geometria tr-osoite)
                                          (sijainnit/tee-geometria alkusijainti loppusijainti))]
                          {:reitit (geo/pg->clj geometria)
                           :alkupisteet pisteet-alku
                           :loppupisteet pisteet-loppu}))
                         tarkastukset)
        reitit (mapv :reitit geometriat)
        alkupisteet (mapv :alkupisteet geometriat)
        loppupisteet (mapv :loppupisteet geometriat)

        yhtena-geometriana (reittitoteuma/yhdista-viivat reitit)]
    yhtena-geometriana
    {:reitti yhtena-geometriana
     :alkupisteet alkupisteet
     :loppupisteet loppupisteet}))

(defn geometrisoi-reittipisteet [db pisteet]
  (reittitoteuma/hae-reitti db pisteet))

(defn- urakan-rajoitusalueet [db urakka-id]
  (let [rajoitusalueet (suolarajoitus-kyselyt/hae-urakan-rajoitusaluegeometriat db {:urakka-id urakka-id})
        rajoitusalueet (map (fn [r]
                              (-> r
                                (update :tierekisteriosoite konv/lue-tr-osoite)
                                (assoc :sijainti (geo/pg->clj (:sijainti r)))))
                         rajoitusalueet)]
    rajoitusalueet))

(defn- hae-suolatoteumat
  "Älä hae tällä liian laajalta aikaväliltä"
  [db tiedot]
  (let [suolat (suolarajoitus-kyselyt/hae-suolatoteumageometriat db tiedot)
        suolat (map (fn [s]
                              (-> s
                                (assoc :sijainti (geo/pg->clj (:sijainti s)))))
                         suolat)]
    suolat))

(defn vaadi-jvh! [palvelu-fn]
  (fn [user payload]
    (if-not (roolit/jvh? user)
      (log/error "DEBUG näkymän palvelua yritti käyttää ei-jvh: " user)
      (do
        (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
        (palvelu-fn payload)))))


(defrecord Debug []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin :as this}]
    (http/julkaise-palvelut
      http
      :debug-hae-toteuman-reitti-ja-pisteet
      (vaadi-jvh! (partial #'hae-toteuman-reitti-ja-pisteet db))
      :debug-geometrisoi-reittitoteuma
      (vaadi-jvh! (partial #'geometrisoi-reittoteuma db))
      :debug-geometrisoi-tarkastus
      (vaadi-jvh! (partial #'geometrisoi-tarkastus db))
      :debug-geometrisoi-reittipisteet
      (vaadi-jvh! (partial #'geometrisoi-reittipisteet db))
      :debug-hae-tyokonehavainto-reittipisteet
      (vaadi-jvh! (partial #'hae-tyokonehavainto-reitti db))
      :debug-hae-seuraava-vapaa-ulkoinen-id
      (vaadi-jvh! (partial #'hae-seuraava-vapaa-ulkoinen-id db))
      :debug-hae-urakan-tierekisteriosoitteita
      (vaadi-jvh! (partial #'hae-urakan-tierekisteriosoitteita db))
      :debug-paivita-raportit
      (vaadi-jvh! (partial #'paivita-raportit db))
      :debug-hae-rajoitusalueet
      (vaadi-jvh! (partial #'urakan-rajoitusalueet db))
      :debug-hae-paivan-suolatoteumat
      (vaadi-jvh! (partial #'hae-suolatoteumat db)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http/poista-palvelut
      http
      :debug-hae-toteuman-reitti-ja-pisteet
      :debug-geometrisoi-reittitoteuma
      :debug-geometrisoi-tarkastus
      :debug-geometrisoi-reittipisteet
      :debug-hae-tyokonehavainto-reittipisteet
      :debug-hae-seuraava-vapaa-ulkoinen-id
      :debug-hae-urakan-tierekisteriosoitteita
      :debug-paivita-raportit
      :debug-hae-rajoitusalueet
      :debug-hae-paivan-suolatoteumat)
    this))
