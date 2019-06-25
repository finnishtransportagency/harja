(ns harja.kyselyt.kanavat.liikennetapahtumat
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            
            [clojure.set :as set]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]

            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]

            [harja.kyselyt.kanavat.kohteet :as kohteet-q]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.lt-ketjutus :as ketjutus]
            [harja.domain.kanavat.kohde :as kohde]
            [clojure.string :as str]
            [clojure.core :as c]))

(defn- liita-kohteen-urakkatiedot [kohteiden-haku tapahtumat]
  (let [kohteet (group-by ::kohde/id (kohteiden-haku (map ::lt/kohde tapahtumat)))]
    (into []
          (map
            #(update % ::lt/kohde
                     (fn [kohde]
                       (if-let [kohteen-urakat (-> kohde ::kohde/id kohteet first ::kohde/urakat)]
                         (assoc kohde ::kohde/urakat kohteen-urakat)
                         (assoc kohde ::kohde/urakat []))))
            tapahtumat))))

(defn- urakat-idlla [urakka-idt tapahtuma]
  (update-in tapahtuma
             [::lt/kohde ::kohde/urakat]
             (fn [urakat]
               (keep
                 #(when (urakka-idt (::ur/id %)) %)
                 urakat))))

(defn- suodata-liikennetapahtuma-toimenpidetyypillä [tiedot tapahtumat]
  (filter #(let [toimenpidetyypit (::toiminto/toimenpiteet tiedot)]
             (if (empty? toimenpidetyypit)
               true
               (some toimenpidetyypit (map ::toiminto/toimenpide (::lt/toiminnot %)))))
          tapahtumat))

(defn- suodata-liikennetapahtuma-aluksen-nimella [tiedot tapahtumat]
  (filter (fn [tapahtuma]
            (let [alus-nimi (::lt-alus/nimi tiedot)]
              (if (empty? alus-nimi)                        ;; Voi olla nil tai ""
                true
                ;; Pidä tapahtuma, jos sen aluksissa ainakin yksi
                ;; alkaa annetulla nimellä
                (not (empty? (lt-alus/suodata-alukset-nimen-alulla
                               (::lt/alukset tapahtuma)
                               alus-nimi))))))
          tapahtumat))

(defn- hae-liikennetapahtumat* [tiedot tapahtumat urakkatiedot-fn urakka-idt]
  (->>
    tapahtumat
    (suodata-liikennetapahtuma-toimenpidetyypillä tiedot)
    (suodata-liikennetapahtuma-aluksen-nimella tiedot)
    (liita-kohteen-urakkatiedot urakkatiedot-fn)
    (map (partial urakat-idlla urakka-idt))
    (remove (comp empty? ::kohde/urakat ::lt/kohde))))

(def ilman-poistettuja-aluksia (map #(update % ::lt/alukset (partial remove ::m/poistettu?))))

(def vain-uittoniput (keep (fn [t]
                             (let [t (update t ::lt/alukset
                                             (partial remove (comp #(or (nil? %) (zero? %)) ::lt-alus/nippulkm)))]
                               (when-not (empty? (::lt/alukset t)) t)))))

(defn- hae-tapahtumien-palvelumuodot* [osien-tiedot tapahtumat]
  (let [id-ja-osat
        (->>
          osien-tiedot
          (group-by ::lt/id)
          (map (fn [[id osat]] [id (get-in osat [0 ::lt/toiminnot])]))
          (into {}))]
    (map
      (fn [tapahtuma]
        (assoc tapahtuma ::lt/toiminnot (id-ja-osat (::lt/id tapahtuma))))
      tapahtumat)))

(defn hae-tapahtumien-palvelumuodot [db tapahtumat]
  (hae-tapahtumien-palvelumuodot*
    (specql/fetch db
                  ::lt/liikennetapahtuma
                  (set/union
                    lt/perustiedot
                    lt/toimintojen-tiedot)
                  {::lt/id (op/in (map ::lt/id tapahtumat))})
    tapahtumat))

(defn- hae-tapahtumien-kohdetiedot* [kohdetiedot tapahtumat]
  (let [id-ja-kohde
        (->>
          kohdetiedot
          (group-by ::kohde/id))]
    (map
      (fn [tapahtuma]
        (assoc tapahtuma ::lt/kohde (first (id-ja-kohde (::lt/kohde-id tapahtuma)))))
      tapahtumat)))

(defn hae-tapahtumien-kohdetiedot [db tapahtumat]
  (hae-tapahtumien-kohdetiedot*
    (specql/fetch db
                  ::kohde/kohde
                  (set/union
                    kohde/perustiedot
                    kohde/kohteenosat)
                  {::kohde/id (op/in (map ::lt/kohde-id tapahtumat))
                   ::m/poistettu? false})
    tapahtumat))

(defn- hae-tapahtumien-perustiedot* [tapahtumat {:keys [niput?]}]
  (into []
        (apply comp
               (remove nil?
                       [(when niput? vain-uittoniput)
                        ;; Jos hakuehdossa otetaan pois poistetut alukset,
                        ;; niin ei palaudu tapahtumat, joiden kaikki alukset ovat poistettuja.
                        ilman-poistettuja-aluksia]))
        tapahtumat))

(defn hae-tapahtumien-perustiedot [db {:keys [aikavali] :as tiedot}]
  (let [urakka-idt (:urakka-idt tiedot)
        kohde-id (get-in tiedot [::lt/kohde ::kohde/id])
        aluslajit (::lt-alus/aluslajit tiedot)
        suunta (::lt-alus/suunta tiedot)
        [alku loppu] aikavali]
    (hae-tapahtumien-perustiedot*
      (specql/fetch db
                    ::lt/liikennetapahtuma
                    (set/union
                      lt/perustiedot
                      lt/kuittaajan-tiedot
                      lt/sopimuksen-tiedot
                      lt/alusten-tiedot
                      ;; Liikennetapahtumalle tarvitaan kohde JA kohteenosat, mutta specql
                      ;; bugittaa eikä saa palautettua kaikkea dataa. Liitetään kohdetiedot erikseen.
                      #{::lt/kohde-id})
                    (op/and
                      (when (and alku loppu)
                        {::lt/aika (op/between alku loppu)})
                      (when kohde-id
                        {::lt/kohde-id kohde-id})
                      (op/and
                        {::m/poistettu? false
                         ::lt/urakka-id (op/in urakka-idt)}
                        (when (or suunta aluslajit)
                          {::lt/alukset (op/and
                                          (when suunta
                                            {::lt-alus/suunta suunta})
                                          {::lt-alus/laji (if (empty? aluslajit)
                                                            (op/in (map name lt-alus/aluslajit))
                                                            (op/in (map name aluslajit)))})}))))
      tiedot)))

(defn hae-liikennetapahtumat [db user tiedot]
  (hae-liikennetapahtumat*
    tiedot
    (->> (hae-tapahtumien-perustiedot db tiedot)
         (hae-tapahtumien-palvelumuodot db)
         (hae-tapahtumien-kohdetiedot db))
    (partial kohteet-q/hae-kohteiden-urakkatiedot db user)
    (:urakka-idt tiedot)))

(defn- hae-kohteen-edellinen-tapahtuma* [tulokset]
  (first
    (sort-by ::lt/aika pvm/jalkeen?
             tulokset)))

(defn- hae-kohteen-edellinen-tapahtuma [db tapahtuma]
  (let [urakka-id (::lt/urakka-id tapahtuma)
        sopimus-id (::lt/sopimus-id tapahtuma)
        kohde-id (::lt/kohde-id tapahtuma)]
    (assert (and urakka-id sopimus-id kohde-id)
            "Urakka-, sopimus-, tai kohde-id puuttuu, ei voida hakea edellistä tapahtumaa.")
    (hae-kohteen-edellinen-tapahtuma*
      (specql/fetch
        db
        ::lt/liikennetapahtuma
        (set/union
          lt/perustiedot)
        {::lt/kohde-id kohde-id
         ::lt/urakka-id urakka-id
         ::lt/sopimus-id sopimus-id}))))

(defn- hae-kuittaamattomat-alukset* [tulokset]
  (into {}
        (map
          (fn [[suunta tapahtumat]]
            [suunta
             (when-let [edelliset
                        (map
                          (fn [[kohde ketjut]]
                            (assoc
                              kohde
                              :edelliset-alukset
                              (map
                                (fn [k]
                                  (merge
                                    (dissoc k
                                            ::ketjutus/tapahtumasta
                                            ::ketjutus/kohteelta
                                            ::ketjutus/alus)
                                    (apply
                                      merge
                                      (map
                                        val
                                        (select-keys k [::ketjutus/tapahtumasta
                                                        ::ketjutus/kohteelta
                                                        ::ketjutus/alus])))))
                                ketjut)))
                          (group-by ::ketjutus/kohteelta tapahtumat))]
               (assert
                 (= 1 (count edelliset))
                 ;; Ketjutus menee aina yksi-yhteen, joten edellisiä kohteita
                 ;; voi samasta suunnasta olla vain yksi
                 "Liikennetapahtumien ketjutuksessa virhe. Kohteelle saapuu aluksia samasta suunnasta, monesta kohteesta.")
               (first edelliset))])
          (group-by (comp ::lt-alus/suunta ::ketjutus/alus) tulokset))))

(defn- hae-kuittaamattomat-alukset [db tapahtuma]
  (let [urakka-id (::lt/urakka-id tapahtuma)
        sopimus-id (::lt/sopimus-id tapahtuma)
        kohde-id (::lt/kohde-id tapahtuma)]
    (assert (and urakka-id sopimus-id kohde-id)
            "Urakka-, sopimus-, tai kohde-id puuttuu, ei voida hakea ketjutustietoja.")
    (hae-kuittaamattomat-alukset*
      (specql/fetch
        db
        ::ketjutus/liikennetapahtuman-ketjutus
        (set/union
          ketjutus/perustiedot
          ketjutus/aluksen-tiedot
          ketjutus/kohteelta-tiedot
          ketjutus/tapahtumasta-tiedot)
        {::ketjutus/kohteelle-id kohde-id
         ::ketjutus/urakka-id urakka-id
         ::ketjutus/sopimus-id sopimus-id
         ::ketjutus/tapahtumaan-id op/null?}))))

(defn hae-edelliset-tapahtumat [db tiedot]
  (let [{:keys [ylos alas]} (hae-kuittaamattomat-alukset db tiedot)
        kohde (hae-kohteen-edellinen-tapahtuma db tiedot)]
    {:ylos ylos
     :alas alas
     :edellinen kohde}))

(defn- alus-kuuluu-tapahtumaan? [db alus tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::lt-alus/liikennetapahtuman-alus
                    #{::lt-alus/id}
                    {::lt-alus/liikennetapahtuma-id (::lt/id tapahtuma)
                     ::lt-alus/id (::lt-alus/id alus)}))))

(defn vaadi-alus-kuuluu-tapahtumaan! [db alus tapahtuma]
  (assert (alus-kuuluu-tapahtumaan? db alus tapahtuma) "Alus ei kuulu tapahtumaan!"))

(defn ketjutus-kuuluu-urakkaan? [db alus-id urakka-id]
  (let [tapahtuma-id (first
                       (map
                         ::ketjutus/tapahtumasta-id
                         (specql/fetch db
                                       ::ketjutus/liikennetapahtuman-ketjutus
                                       #{::ketjutus/tapahtumasta-id}
                                       {::ketjutus/alus-id alus-id})))]
    (boolean
      (when tapahtuma-id
        (not-empty
          (specql/fetch db
                        ::lt/liikennetapahtuma
                        #{::lt/urakka-id ::lt/id}
                        {::lt/id tapahtuma-id
                         ::lt/urakka-id urakka-id}))))))

(defn poista-ketjutus! [db alus-id urakka-id]
  (when (ketjutus-kuuluu-urakkaan? db alus-id urakka-id)
    (specql/delete! db
                    ::ketjutus/liikennetapahtuman-ketjutus
                    {::ketjutus/alus-id alus-id
                     ::ketjutus/tapahtumaan-id op/null?})))

(defn vapauta-ketjutus! [db tapahtuma-id]
  (specql/update! db
                  ::ketjutus/liikennetapahtuman-ketjutus
                  {::ketjutus/tapahtumaan-id nil}
                  {::ketjutus/tapahtumaan-id tapahtuma-id}))

(defn poista-alus! [db user alus tapahtuma]
  (vaadi-alus-kuuluu-tapahtumaan! db alus tapahtuma)
  (specql/update! db
                  ::lt-alus/liikennetapahtuman-alus
                  (merge
                    {::m/poistaja-id (:id user)
                     ::m/poistettu? true
                     ::m/muokattu (pvm/nyt)}
                    alus)
                  {::lt-alus/id (::lt-alus/id alus)})

  (poista-ketjutus! db (::lt-alus/id alus) (::lt/urakka-id tapahtuma)))

(defn tallenna-alus-tapahtumaan! [db user alus tapahtuma]
  (let [olemassa? (id-olemassa? (::lt-alus/id alus))
        alus (assoc alus ::lt-alus/liikennetapahtuma-id (::lt/id tapahtuma))]



    (if (and olemassa? (::m/poistettu? alus))
      (poista-alus! db user alus tapahtuma)

      (if (and olemassa? (alus-kuuluu-tapahtumaan? db alus tapahtuma))
        (do
          (specql/update! db
                          ::lt-alus/liikennetapahtuman-alus
                          (merge
                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}
                            alus)
                          {::lt-alus/id (::lt-alus/id alus)})
          ;; Palauta luotu alus
          alus)

        (specql/insert! db
                        ::lt-alus/liikennetapahtuman-alus
                        (merge
                          {::m/luoja-id (:id user)}
                          (->
                            (->> (keys alus)
                                 (filter #(= (namespace %) "harja.domain.kanavat.lt-alus"))
                                 (select-keys alus))
                            (dissoc ::lt-alus/id))))))))

(defn- osa-kuuluu-tapahtumaan? [db osa tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::toiminto/liikennetapahtuman-toiminto
                    #{::toiminto/id}
                    {::toiminto/liikennetapahtuma-id (::lt/id tapahtuma)
                     ::toiminto/id (::toiminto/id osa)}))))

(defn vaadi-osa-kuuluu-tapahtumaan! [db osa tapahtuma]
  (assert (osa-kuuluu-tapahtumaan? db osa tapahtuma) "Alus ei kuulu tapahtumaan!"))

(defn tallenna-osa-tapahtumaan! [db user osa tapahtuma]
  (let [olemassa? (id-olemassa? (::toiminto/id osa))
        osa (assoc osa ::toiminto/liikennetapahtuma-id (::lt/id tapahtuma)
                       ::toiminto/kohde-id (::lt/kohde-id tapahtuma))]
    (if olemassa?
      (do
        (vaadi-osa-kuuluu-tapahtumaan! db osa tapahtuma)
        (specql/update! db
                        ::toiminto/liikennetapahtuman-toiminto
                        (merge
                          (if (::m/poistettu? osa)
                            {::m/poistaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)})
                          (into {} (filter (comp some? val) osa)))
                        {::toiminto/id (::toiminto/id osa)}))

      (specql/insert! db
                      ::toiminto/liikennetapahtuman-toiminto
                      (merge
                        {::m/luoja-id (:id user)}
                        ;; Poistetaan kentät joissa arvo on nil, specql ei tykännyt
                        (into {} (filter (comp some? val) osa)))))))

(defn tapahtuma-kuuluu-urakkaan? [db tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::lt/liikennetapahtuma
                    #{::lt/id}
                    {::lt/id (::lt/id tapahtuma)
                     ::lt/urakka-id (::lt/urakka-id tapahtuma)}))))

(defn vaadi-tapahtuma-kuuluu-urakkaan! [db tapahtuma]
  (assert (tapahtuma-kuuluu-urakkaan? db tapahtuma) "Tapahtuma ei kuulu urakkaan!"))

(defn kuittaa-vanhat-ketjutukset! [db tapahtuma]
  (specql/update! db
                  ::ketjutus/liikennetapahtuman-ketjutus
                  {::ketjutus/tapahtumaan-id (::lt/id tapahtuma)}
                  {::ketjutus/alus-id (op/in (map ::lt-alus/id (::lt/alukset tapahtuma)))
                   ::ketjutus/kohteelle-id (::lt/kohde-id tapahtuma)}))

(defn ketjutus-olemassa? [db alus]
  (not-empty
    (specql/fetch db
                  ::ketjutus/liikennetapahtuman-ketjutus
                  #{::ketjutus/alus-id}
                  {::ketjutus/alus-id (::lt-alus/id alus)})))

(defn- hae-seuraavat-kohteet* [kohteet]
  (mapcat vals kohteet))

(defn hae-seuraavat-kohteet [db kohteelta-id suunta]
  (hae-seuraavat-kohteet*
    (specql/fetch
      db
      ::kohde/kohde
      (if (= suunta :ylos)
        #{::kohde/ylos-id}
        #{::kohde/alas-id})
      {::kohde/id kohteelta-id})))

;; specql:n insert ei käytä paluuarvoihin transformaatioita, eli kun tallennuksessa
;; insertoidaan uusia aluksia, niiden ::suunta on merkkijono. Keywordin ja merkkijonon
;; vertailu on toki yksinkertaista, mutta tein tämän funktion turvatakseni tulevaisuutta -
;; voi hyvin olla, että jossain tulevassa specql-päivityksessä transformaatiot vaikuttavat myös
;; inserttien paluuarvoihin.
(defn- sama-suunta?
  "Vertailee kahta suuntaa. Suunnat voivat olla keywordejä tai merkkijonoja."
  [a b]
  (cond
    (and (keyword? a) (keyword? b))
    (= a b)

    (and (string? a) (string? b))
    (= a b)

    (and (keyword? a) (string? b))
    (= (name a) b)

    (and (string? a) (keyword? b))
    (= (keyword a) b)

    :else false))

(defn luo-uusi-ketjutus! [db tapahtuma]
  (let [alukset (::lt/alukset tapahtuma)
        kohteelta-id (::lt/kohde-id tapahtuma)
        ;; Kun uusi alus palautuu insertistä, suunta on merkkijono
        suunnat (into #{} (map (comp keyword ::lt-alus/suunta) alukset))]
    (doseq [suunta suunnat]
      (doseq [kohteelle-id (hae-seuraavat-kohteet db kohteelta-id suunta)]
        (doseq [alus (filter (comp (partial sama-suunta? suunta) ::lt-alus/suunta) alukset)]
          (specql/upsert! db
                          ::ketjutus/liikennetapahtuman-ketjutus
                          #{::ketjutus/alus-id}
                          {::ketjutus/kohteelle-id kohteelle-id
                           ::ketjutus/kohteelta-id kohteelta-id
                           ::ketjutus/alus-id (::lt-alus/id alus)
                           ::ketjutus/tapahtumasta-id (::lt/id tapahtuma)

                           ::ketjutus/urakka-id (::lt/urakka-id tapahtuma)
                           ::ketjutus/sopimus-id (::lt/sopimus-id tapahtuma)}))))))

(defn poista-toiminto! [db user toiminto]
  (specql/update! db
                  ::toiminto/liikennetapahtuman-toiminto
                  {::m/poistaja-id (:id user)
                   ::m/muokattu (pvm/nyt)
                   ::m/poistettu? true}
                  {::toiminto/id (::toiminto/id toiminto)}))

(defn poista-tapahtuma! [db user tapahtuma]
  (specql/update! db
                  ::lt/liikennetapahtuma
                  (merge
                    {::m/poistaja-id (:id user)
                     ::m/poistettu? true
                     ::m/muokattu (pvm/nyt)}
                    (dissoc tapahtuma
                            ::lt/alukset
                            ::lt/toiminnot))
                  {::lt/id (::lt/id tapahtuma)})

  (vapauta-ketjutus! db (::lt/id tapahtuma))

  (doseq [alus (::lt/alukset tapahtuma)]
    (poista-alus! db user alus tapahtuma))

  (doseq [toiminto (::lt/toiminnot tapahtuma)]
    (poista-toiminto! db user toiminto)))

(defn tallenna-liikennetapahtuma! [db user tapahtuma]
  (jdbc/with-db-transaction [db db]
    (jdbc/execute! db ["SET CONSTRAINTS ALL DEFERRED"])
    (if (::m/poistettu? tapahtuma)
      (poista-tapahtuma! db user tapahtuma)
      (let [olemassa? (id-olemassa? (::lt/id tapahtuma))
            uusi-tapahtuma (if olemassa?
                             (do
                               (vaadi-tapahtuma-kuuluu-urakkaan! db tapahtuma)
                               (specql/update! db
                                               ::lt/liikennetapahtuma
                                               (merge
                                                 {::m/muokkaaja-id (:id user)
                                                  ::m/muokattu (pvm/nyt)}
                                                 (-> tapahtuma
                                                     (update ::lt/vesipinta-alaraja #(when-not (nil? %)
                                                                                       (bigdec %)))
                                                     (update ::lt/vesipinta-ylaraja #(when-not (nil? %)
                                                                                       (bigdec %)))
                                                     (dissoc ::lt/alukset ::lt/toiminnot)))
                                               {::lt/id (::lt/id tapahtuma)})
                               ;; Palautetaan tapahtuma alkuperäisenä, koska update palauttaa vain ykkösen.
                               ;; Ei haeta kannasta uudelleen, koska se on turhaa.
                               tapahtuma)
                             (specql/insert! db
                                             ::lt/liikennetapahtuma
                                             (merge
                                               {::m/luoja-id (:id user)}
                                               (dissoc tapahtuma
                                                       ::lt/id
                                                       ::lt/alukset
                                                       ::lt/toiminnot))))]
        (doseq [osa (::lt/toiminnot tapahtuma)]
          (tallenna-osa-tapahtumaan! db user osa uusi-tapahtuma))

        (kuittaa-vanhat-ketjutukset! db (assoc tapahtuma ::lt/id (::lt/id uusi-tapahtuma)))

        (let [alukset
              (doall
                (for [alus (::lt/alukset tapahtuma)]
                  (tallenna-alus-tapahtumaan! db user alus uusi-tapahtuma)))]


          (luo-uusi-ketjutus! db (assoc tapahtuma ::lt/alukset (remove ::m/poistettu? alukset)
                                                  ::lt/id (::lt/id uusi-tapahtuma))))))))
