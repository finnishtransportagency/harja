(ns harja.kyselyt.tielupa-kyselyt
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [specql.core :refer [fetch update! insert! upsert!]]
    [specql.op :as op]
    [jeesql.core :refer [defqueries]]
    [clojure.set :as set]
    [harja.id :refer [id-olemassa?]]
    [harja.domain.tielupa :as tielupa]
    [harja.domain.alueurakka-domain :as alueurakka]
    [harja.kyselyt.konversio :as konv]
    [clojure.data.json :as json]
    [harja.pvm :as pvm]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [taoensso.timbre :as log]
    [harja.geo :as geo])
  (:import (org.postgis PGgeometry)))

;; Ajaetaan jeesql:ssä haun yhteydessä
(defn muunna-hakija [hakija]
  (let [avaimet {:hakija-nimi ::tielupa/hakija-nimi}
        hakija (set/rename-keys hakija avaimet)]
    hakija))

(defqueries "harja/kyselyt/tielupa_kyselyt.sql"
            {:positional? true})

(defn hae-tieluvat [db hakuehdot]
  (fetch db
    ::tielupa/tielupa
    (set/union
      harja.domain.tielupa/perustiedot
      harja.domain.tielupa/hakijan-tiedot
      harja.domain.tielupa/urakoitsijan-tiedot
      harja.domain.tielupa/liikenneohjaajan-tiedot
      harja.domain.tielupa/tienpitoviranomaisen-tiedot
      harja.domain.tielupa/johto-ja-kaapeliluvan-tiedot)
    hakuehdot))

(defn suodata-urakalla [tieluvat urakka-id]
  (if urakka-id
    (filter (fn [{::tielupa/keys [urakat]}]
              (some #(= % urakka-id) urakat))
            tieluvat)
    tieluvat))

(defn tielupien-liitteet [db tieluvat]
  (let [liitteet (hae-tielupien-liitteet db (map ::tielupa/id tieluvat))]
    (mapcat
      val
      (merge-with
        (fn [liitteet lupa]
          [(assoc (first lupa) ::tielupa/liitteet liitteet)])
        (group-by :tielupa liitteet) (group-by ::tielupa/id tieluvat)))))

(defn hae-tielupa [db id]
  (let [lupa (fetch db ::tielupa/tielupa tielupa/kaikki-kentat {::tielupa/id id})]
    (first (tielupien-liitteet db lupa))))

(defn hae-tieluvat-hakunakymaan [db hakuehdot]
  (let [_ (log/debug "hae-tieluvat-hakunakymaan :: hakuehdot" hakuehdot)
        tr-osoite (:harja.domain.tielupa/haettava-tr-osoite hakuehdot)
        hakumap {:urakka-id (or (:urakka-id hakuehdot) nil)
                 :alueurakkanro (or (:alueurakkanro hakuehdot) nil)
                 :hakija-nimi (or (::tielupa/hakija-nimi hakuehdot) nil)
                 :tyyppi (when (::tielupa/tyyppi hakuehdot) (name (::tielupa/tyyppi hakuehdot)))
                 :paatoksen-diaarinumero (when (and (::tielupa/paatoksen-diaarinumero hakuehdot)
                                                 (not= "" (::tielupa/paatoksen-diaarinumero hakuehdot)))
                                           (::tielupa/paatoksen-diaarinumero hakuehdot))
                 :voimassaolon-alkupvm (or (::tielupa/voimassaolon-alkupvm hakuehdot) nil)
                 :voimassaolon-loppupvm (or (::tielupa/voimassaolon-loppupvm hakuehdot))
                 :myonnetty-alkupvm (when (and (:myonnetty hakuehdot) (first (:myonnetty hakuehdot)))
                                      (first (:myonnetty hakuehdot)))
                 :myonnetty-loppupvm (when (and (:myonnetty hakuehdot) (second (:myonnetty hakuehdot)))
                                       (second (:myonnetty hakuehdot)))
                 :tie (when (and tr-osoite (::tielupa/tie tr-osoite)) (::tielupa/tie tr-osoite))
                 :aosa (when (and tr-osoite (::tielupa/tie tr-osoite)) (::tielupa/aosa tr-osoite))
                 :aet (when (and tr-osoite (::tielupa/tie tr-osoite)) (::tielupa/aet tr-osoite))
                 :losa (when (and tr-osoite (::tielupa/tie tr-osoite)) (::tielupa/losa tr-osoite))
                 :let (when (and tr-osoite (::tielupa/tie tr-osoite)) (::tielupa/let tr-osoite))}
        ;; Tämä on tehty raalla sql:llä, koska specql:n geometriamäppäys on liian hidas isoilla tietomäärillä
        ;; Muutos nopeuttaa geometrian käsittelyä kymmenkertaisesti eli 2s -> 200ms
        ;; Hakuoehtojen organisaation alueeseen kuuluminen on myös pakko tehdä raalla sql:llä, kun specql ei siihen taivu helposti.
        tulokset (hae-tienpidon-luvat db hakumap)
        mapatyt-tulokset (map (fn [lupa]
                                (let [lupa (-> lupa
                                             (update :tyyppi keyword)
                                             (set/rename-keys tielupa/tieluvatsql->tieluvat-domain))
                                      sijainnit (mapv
                                                  (fn [sij]
                                                    (let [sij (set/rename-keys sij tielupa/sijainnit->domain-tielupa)
                                                          sij (if (:f6 sij)
                                                                (assoc sij ::tielupa/geometria (PGgeometry. (:f6 sij)))
                                                                sij)
                                                          sij (dissoc sij :f6)]
                                                      sij))
                                                  (konv/jsonb->clojuremap (:sijainnit lupa)))
                                      liikennemerkkijarjestelyt (mapv
                                                                  (fn [l]
                                                                    (let [l (set/rename-keys l {:f1 ::tielupa/liikennemerkki})
                                                                          l (if-not (nil? (:f2 l))
                                                                              (assoc l ::tielupa/geometria (PGgeometry. (:f2 l)))
                                                                              l)
                                                                          l (dissoc l :f2)]
                                                                      l))
                                                                  (konv/jsonb->clojuremap (:liikennemerkkijarjestelyt lupa)))]
                                  (-> lupa
                                    (assoc ::tielupa/sijainnit sijainnit)
                                    (assoc ::tielupa/liikennemerkkijarjestelyt liikennemerkkijarjestelyt)
                                    (dissoc :sijainnit :liikennemerkkijarjestelyt))))
                           tulokset)]
    mapatyt-tulokset))

(defn hae-tielupien-hakijat [db hakuteksti]
  (tielupien-hakijat db {:hakuteksti (str hakuteksti "%")}))

(defn hae-ulkoisella-tunnistella [db ulkoinen-id]
  (first (hae-tieluvat db {::tielupa/ulkoinen-tunniste ulkoinen-id})))

(defn onko-olemassa-ulkoisella-tunnisteella? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-tieluvat db {::tielupa/ulkoinen-tunniste ulkoinen-id})))))

(defn tallenna-tielupa [db tielupa]
  (let [id (::tielupa/id tielupa)
        ulkoinen-tunniste (::tielupa/ulkoinen-tunniste tielupa)
        uusi (assoc tielupa ::muokkaustiedot/luotu (pvm/nyt))
        muokattu (assoc tielupa ::muokkaustiedot/muokattu (pvm/nyt))]
    (if (id-olemassa? id)
      (update! db ::tielupa/tielupa muokattu {::tielupa/id id})
      (if (onko-olemassa-ulkoisella-tunnisteella? db ulkoinen-tunniste)
        (update! db ::tielupa/tielupa muokattu {::tielupa/ulkoinen-tunniste ulkoinen-tunniste})
        (insert! db ::tielupa/tielupa uusi)))))

(defn hae-alueurakat [db]
  (let [alueurakat (fetch db ::alueurakka/alueurakka
                      #{::alueurakka/alueurakkanro ::alueurakka/nimi}
                     {}
                     {:specql.core/order-by ::alueurakka/elynumero
                     :specql.core/order-direction :asc})]
    alueurakat))
