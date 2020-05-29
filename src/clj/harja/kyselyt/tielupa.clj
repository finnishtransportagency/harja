(ns harja.kyselyt.tielupa
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [specql.core :refer [fetch update! insert! upsert!]]
    [specql.op :as op]
    [jeesql.core :refer [defqueries]]
    [clojure.set :as set]
    [slingshot.slingshot :refer [try+]]
    [harja.id :refer [id-olemassa?]]
    [harja.domain
     [tielupa :as tielupa]
     [muokkaustiedot :as muokkaustiedot]
     [oikeudet :as oikeudet]]
    [harja.pvm :as pvm]
    [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/tielupa.sql"
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

(defn overlaps? [rivi-alku rivi-loppu alku loppu]
  (op/or {rivi-alku (op/between alku loppu)}
         {rivi-loppu (op/between alku loppu)}
         {rivi-alku (op/<= alku) rivi-loppu (op/>= loppu)}))

(defn sama-tie? [tie sijainti]
  (= tie (get-in sijainti [::tielupa/tie])))

(defn tr-piste-aiemmin-tai-sama? [osa-a et-a osa-b et-b]
  (boolean
    (or (< osa-a osa-b)
        (and (= osa-a osa-b)
             (<= et-a et-b)))))


(defn valilla? [[aosa aet :as alku] [losa loet :as loppu] sijainti]
  (let [[[aosa aet :as alku]
         [losa loet :as loppu]]
        ;; Varmistetaan, että alku on pienempi kuin loppu, yksinkertaistaa logiikkaa
        (cond
          ;; Vain alkuosa annettu
          (nil? loet)
          [alku loppu]

          (> aosa losa)
          [loppu alku]

          (and (= aosa losa) (> aet loet))
          [loppu alku]

          :else
          [alku loppu])

        tl-aosa (get-in sijainti [::tielupa/aosa])
        tl-aet (get-in sijainti [::tielupa/aet])
        tl-losa (get-in sijainti [::tielupa/losa])
        tl-let (get-in sijainti [::tielupa/let])]

    (boolean
      (cond
        ;; Molemmat on pisteitä
        (and (nil? losa) (nil? loet) (nil? tl-losa) (nil? tl-let))
        (and (= aosa tl-aosa) (= aet tl-aet))

        ;; Tielupa on piste, hakuehto on väli
        (and (some? losa) (some? loet) (nil? tl-losa) (nil? tl-let))
        (and (tr-piste-aiemmin-tai-sama? aosa aet tl-aosa tl-aet)
             (tr-piste-aiemmin-tai-sama? tl-aosa tl-aet losa loet))

        ;; Tielupa on väli, hakuehto on piste
        (and (nil? losa) (nil? loet) (some? tl-losa) (some? tl-let))
        (and (tr-piste-aiemmin-tai-sama? tl-aosa tl-aet aosa aet)
             (tr-piste-aiemmin-tai-sama? aosa aet tl-losa tl-let))

        ;; Molemmat on välejä
        (and (some? losa) (some? loet) (some? tl-losa) (some? tl-let))
        (or
          ;; hakuehdon alkupiste on tieluvan välissä
          (and (tr-piste-aiemmin-tai-sama? tl-aosa tl-aet aosa aet)
               (tr-piste-aiemmin-tai-sama? aosa aet tl-losa tl-let))
          ;; tieluvan alkupiste on hakuehdon välissä
          (and (tr-piste-aiemmin-tai-sama? aosa aet tl-aosa tl-aet)
               (tr-piste-aiemmin-tai-sama? tl-aosa tl-aet losa loet)))

        :else
        false))))


;; TODO: entä tiesoitteettomat luvat?
(defn suodata-tieosoitteella [tieluvat sijainnit]
  (let [tie (::tielupa/tie sijainnit)
        aosa (::tielupa/aosa sijainnit)
        aet (::tielupa/aet sijainnit)
        losa (::tielupa/losa sijainnit)
        let (::tielupa/let sijainnit)]
    (cond
      (and tie aosa aet)
      (filterv
        ;; Tieluvalla voi olla monta sijaintia. On tielupia joissa ei ole tieosoitetta (alueurakka kuitenkin löytyy).
        ;; Jos yhdenkään sijainnin tr-osoite osuu hakuvälille, palautetaan lupa.
        (comp
          (partial some
                   (every-pred (partial valilla? [aosa aet] [losa let])
                               (partial sama-tie? tie)))
          ::tielupa/sijainnit)
        tieluvat)

      (some? tie)
      (filterv
        (comp
          (partial some
                   (every-pred
                     (partial sama-tie? tie)))
          ::tielupa/sijainnit)
        tieluvat)

      :default
      tieluvat)))

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

(defn tarkasta-oikeus-katselmukseen [tieluvat user]
  (doall (map (fn [{::tielupa/keys [urakat] :as tielupa}]
                (let [kayttajalla-oikeus-urakan-katselmus-urliin? (and (every? (fn [urakka-id]
                                                                                 (oikeudet/on-muu-oikeus? "katselmus-url"
                                                                                                          oikeudet/hallinta-tienpidonluvat
                                                                                                          urakka-id
                                                                                                          user))
                                                                               urakat)
                                                                       (not (nil? urakat)))]
                  (if kayttajalla-oikeus-urakan-katselmus-urliin?
                    tielupa
                    (assoc tielupa ::tielupa/katselmus-url nil))))
              tieluvat)))

(defn hae-tieluvat-hakunakymaan [db user hakuehdot]
  (->
    (fetch db
           ::tielupa/tielupa
           tielupa/kaikki-kentat
           (op/and
             (when-let [nimi (::tielupa/hakija-nimi hakuehdot)]
               {::tielupa/hakija-nimi nimi})
             (when-let [tyyppi (::tielupa/tyyppi hakuehdot)]
               {::tielupa/tyyppi tyyppi})
             (when-let [tunniste (::tielupa/paatoksen-diaarinumero hakuehdot)]
               {::tielupa/paatoksen-diaarinumero (op/ilike (str "%" tunniste "%"))})
             (let [alku (::tielupa/voimassaolon-alkupvm hakuehdot)
                   loppu (::tielupa/voimassaolon-loppupvm hakuehdot)]
               (cond
                 (and alku loppu)
                 (overlaps? ::tielupa/voimassaolon-alkupvm
                            ::tielupa/voimassaolon-loppupvm
                            alku
                            loppu)

                 :else nil))
             (let [[alku loppu] (:myonnetty hakuehdot)]
               (cond
                 (and alku loppu)
                 {::tielupa/myontamispvm (op/between alku loppu)}

                 :else nil))))
    (suodata-tieosoitteella (::tielupa/haettava-tr-osoite hakuehdot))
    (suodata-urakalla (:urakka-id hakuehdot))
    (tarkasta-oikeus-katselmukseen user)
    ((partial tielupien-liitteet db))))

(defn hae-tielupien-hakijat [db hakuteksti]
  (set
    (fetch db
           ::tielupa/tielupa
           #{::tielupa/hakija-nimi}
           {::tielupa/hakija-nimi (op/ilike (str hakuteksti "%"))})))

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


