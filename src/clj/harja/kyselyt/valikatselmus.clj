(ns harja.kyselyt.valikatselmus
  (:require [specql.core :refer [fetch update! insert! columns]]
            [jeesql.core :refer [defqueries]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.urakka :as urakka]
            [harja.pvm :as pvm]))

(defqueries "harja/kyselyt/valikatselmus.sql"
  {:positional? true})

(declare hae-urakan-hintapaatokset hintapaatos-tehty? hae-urakan-bonuksen-toimenpideinstanssi-id
  hae-oikaistu-tavoitehinta hae-oikaistu-kattohinta hae-bonukset hae-sanktiot hae-arvonvahennykset hae-tavoitehinnan-muutokset-hoitokaudelle
  hae-hoitokauden-alun-indeksikorjattu-tavoitehinta hae-hoitokauden-lopun-indeksikorjaamaton-tavoitehinta
  poista-tavoitehinnan-muutos! hae-urakan-tavoitehintaan-vaikuttavat-muutokset-analytiikalle)

;; Tavoitehinnan oikaisut

(defn hae-oikaisu [db oikaisun-id]
  (first (fetch db ::valikatselmus/tavoitehinnan-oikaisu
           (columns ::valikatselmus/tavoitehinnan-oikaisu)
           {::valikatselmus/oikaisun-id oikaisun-id})))

(defn hae-oikaisut [db {::urakka/keys [id]}]
  (group-by ::valikatselmus/hoitokauden-alkuvuosi
    (fetch db ::valikatselmus/tavoitehinnan-oikaisu
      (columns ::valikatselmus/tavoitehinnan-oikaisu)
      {::urakka/id id ::muokkaustiedot/poistettu? false}
      {:specql.core/order-by ::valikatselmus/oikaisun-id})))

(defn tee-oikaisu [db oikaisu]
  (insert! db ::valikatselmus/tavoitehinnan-oikaisu oikaisu))

(defn paivita-oikaisu [db oikaisu]
  (let [;; Päivitä oikaisu palauttaa vain päivitettyjen rivien määrän
        _ (update! db ::valikatselmus/tavoitehinnan-oikaisu
            oikaisu
            {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id oikaisu)})

        ;; Haetaan id:n perusteella juuri päivitetty oikaisu
        paivitetty (first
                     (fetch db ::valikatselmus/tavoitehinnan-oikaisu
                       (columns ::valikatselmus/tavoitehinnan-oikaisu)
                       {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id oikaisu)}))]
    paivitetty))

(defn poista-oikaisu [db oikaisu kayttaja]
  (poista-tavoitehinnan-muutos! db {:id (::valikatselmus/oikaisun-id oikaisu)
                                    :muokkaaja-id (:id kayttaja)}))

;; Kattohinnan oikaisut

(defn hae-kattohinnan-oikaisut
  "Palauta map, jonka avaimena on hoitokauden alkuvuosi, ja arvona kattohinnan oikaisu kyseiselle vuodelle.
  HUOM: hae-oikaisut -funktion arvona on lista oikaisuja."
  [db {::urakka/keys [id]}]
  (reduce
    (fn [vuosi->oikaisu {::valikatselmus/keys [hoitokauden-alkuvuosi] :as oikaisu}]
      (assoc vuosi->oikaisu hoitokauden-alkuvuosi oikaisu))
    {}
    (fetch
      db
      ::valikatselmus/kattohinnan-oikaisu
      (columns ::valikatselmus/kattohinnan-oikaisu)
      {::urakka/id id
       ::muokkaustiedot/poistettu? false})))

(defn hae-kattohinnan-oikaisu
  "Hae kattohinnan oikaisu annetulle urakalle ja hoitokauden alkuvuodelle.
  Palauttaa oikaisu-mapin tai nil-arvon: näitä voi olla korkeintaan yksi."
  [db urakka-id hoitokauden-alkuvuosi]
  (first
    (fetch db ::valikatselmus/kattohinnan-oikaisu
      (columns ::valikatselmus/kattohinnan-oikaisu)
      {::urakka/id urakka-id
       ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))

(defn tee-kattohinnan-oikaisu [db oikaisu]
  (insert! db ::valikatselmus/kattohinnan-oikaisu oikaisu))

(defn paivita-kattohinnan-oikaisu [db oikaisu]
  (update! db ::valikatselmus/kattohinnan-oikaisu
    oikaisu
    {::valikatselmus/kattohinnan-oikaisun-id (::valikatselmus/kattohinnan-oikaisun-id oikaisu)}))

(defn poista-kattohinnan-oikaisu [db urakka-id hoitokauden-alkuvuosi kayttaja]
  (update!
    db
    ::valikatselmus/kattohinnan-oikaisu
    {::muokkaustiedot/poistettu? true
     ::muokkaustiedot/muokkaaja-id (:id kayttaja)
     ::muokkaustiedot/muokattu (pvm/nyt)}
    {::urakka/id urakka-id
     ::valikatselmus/hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))


