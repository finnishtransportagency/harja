(ns harja.kyselyt.vesivaylat.hinnoittelut
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            
            [clojure.set :as set]
            [clj-time.core :as t]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]

            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]

            [harja.pvm :as pvm]

            [harja.kyselyt.vesivaylat.toimenpiteet :as to-q]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.kommentti :as kommentti]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.konversio :as konv]))

(defn- vaadi-hinnoittelut-kuuluvat-urakkaan* [tulos hinnoittelu-idt urakka-id]
  (when (or
          (nil? urakka-id)
          (some nil? hinnoittelu-idt)
          (not (->> tulos
                   (map ::h/urakka-id)
                   (every? (partial = urakka-id)))))
    (throw (SecurityException. (str "Hinnoittelut " hinnoittelu-idt " eivät kuulu urakkaan " urakka-id)))))

(defn vaadi-hinnoittelut-kuuluvat-urakkaan [db hinnoittelu-idt urakka-id]
  (vaadi-hinnoittelut-kuuluvat-urakkaan*
    (specql/fetch
     db
     ::h/hinnoittelu
     (set/union h/perustiedot h/viittaus-idt)
     {::h/id (op/in hinnoittelu-idt)})
    hinnoittelu-idt
    urakka-id))

(defn- vaadi-hinnat-kuuluvat-toimenpiteeseen* [tulos hinta-idt toimenpide-id]
  (let [toimenpiteen-hinta-idt (->> tulos
                                    (mapcat ::to/hinnoittelu-linkit)
                                    (mapcat (comp ::h/hinnat ::h/hinnoittelut))
                                    (map ::hinta/id)
                                    (into #{}))]
    (when (or
            (some nil? hinta-idt)
            (some nil? toimenpiteen-hinta-idt)
            (not (set/subset? (set hinta-idt) toimenpiteen-hinta-idt)))
      (throw (SecurityException. (str "Hinnat " hinta-idt " eivät kuulu toimenpiteeseen " toimenpide-id))))))

(defn vaadi-hinnat-kuuluvat-toimenpiteeseen [db hinta-idt toimenpide-id]
  (vaadi-hinnat-kuuluvat-toimenpiteeseen*
    (specql/fetch
     db
     ::to/reimari-toimenpide
     (set/union to/perustiedot to/hinnoittelu)
     {::to/id toimenpide-id})
    hinta-idt
    toimenpide-id))

(defn- vaadi-tyot-kuuluvat-toimenpiteeseen* [toimenpiteet tyot tyo-idt toimenpide-id]
  (let [toimenpiteen-hinnoittelu-idt (set (->> toimenpiteet
                                               (mapcat ::to/hinnoittelu-linkit)
                                               (map ::h/hinnoittelut)
                                               (map ::h/id)))
        toiden-hinnoittelu-idt (set (map ::tyo/hinnoittelu-id tyot))]
    (when (not (every? #(toimenpiteen-hinnoittelu-idt %) toiden-hinnoittelu-idt))
      (throw (SecurityException. (str "Työt " tyo-idt " eivät kuulu toimenpiteeseen " toimenpide-id))))))

(defn vaadi-tyot-kuuluvat-toimenpiteeseen [db tyo-idt toimenpide-id]
  (vaadi-tyot-kuuluvat-toimenpiteeseen*
    (specql/fetch
     db
     ::to/reimari-toimenpide
     (set/union to/perustiedot to/hinnoittelu)
     {::to/id toimenpide-id})
    (specql/fetch
      db
      ::tyo/tyo
      tyo/viittaus-idt
      {::tyo/id (op/in tyo-idt)})
    tyo-idt
    toimenpide-id))

(defn- vaadi-hinnat-kuuluvat-hinnoitteluun* [hinnat hinta-idt hinnoittelu-id]
  (when (or
          (nil? hinnoittelu-id)
          (not
           (->> hinnat
                (map ::hinta/hinnoittelu-id)
                (every? (partial = hinnoittelu-id)))))
    (throw (SecurityException. (str "Hinnat " hinta-idt " eivät kuulu hinnoiteluun " hinnoittelu-id)))))

(defn vaadi-hinnat-kuuluvat-hinnoitteluun [db hinta-idt hinnoittelu-id]
  (vaadi-hinnat-kuuluvat-hinnoitteluun*
    (specql/fetch
     db
     ::hinta/hinta
     (set/union hinta/perustiedot hinta/viittaus-idt)
     {::hinta/id (op/in hinta-idt)})
    hinta-idt
    hinnoittelu-id))

(defn hinnoitteluun-kuuluu-toimenpiteita?* [toimenpiteet]
  (not (empty? toimenpiteet)))

(defn hinnoitteluun-kuuluu-toimenpiteita? [db hinnoittelu-id]
  (hinnoitteluun-kuuluu-toimenpiteita?*
    (specql/fetch
     db
     ::h/hinnoittelu<->toimenpide
     #{::h/hinnoittelu-id}
     {::h/hinnoittelu-id (op/in #{hinnoittelu-id})
      ::m/poistettu? false})))

(defn vaadi-hinnoitteluun-ei-kuulu-toimenpiteita [db hinnoittelu-id]
  (when (hinnoitteluun-kuuluu-toimenpiteita? db hinnoittelu-id)
    (throw (RuntimeException. "Hinnoitteluun kuuluu toimenpiteitä."))))

(defn hae-hintaryhmat [db urakka-id]
  (->> (specql/fetch db
                     ::h/hinnoittelu
                     (set/union h/perustiedot h/hinnat)
                     {::h/urakka-id urakka-id
                      ::h/hintaryhma? true
                      ::m/poistettu? false})
       (mapv #(assoc % ::h/hinnat (remove ::m/poistettu? (::h/hinnat %))))
       (mapv #(assoc % ::h/tyhja? (not (hinnoitteluun-kuuluu-toimenpiteita? db (::h/id %)))))
       (to-q/liita-laskutuslupatiedot-hinnoitteluihin db)))

(defn luo-hinnoittelu! [db user tiedot]
  (let [urakka-id (::ur/id tiedot)
        nimi (::h/nimi tiedot)]
    (assoc (specql/insert! db
                           ::h/hinnoittelu
                           {::h/urakka-id urakka-id
                            ::h/nimi nimi
                            ::h/hintaryhma? true
                            ::m/luoja-id (:id user)})
      ::h/tyhja? true)))

(defn poista-hinnoittelu! [db user hinnoittelu-id]
  (specql/update! db
                  ::h/hinnoittelu
                  {::m/poistettu? true
                   ::m/poistaja-id (:id user)}
                  {::h/id hinnoittelu-id}))

(defn poista-toimenpiteet-hintaryhmistaan! [db user toimenpide-idt]
  (let [hintaryhma-idt (set (map ::h/id (specql/fetch db
                                                      ::h/hinnoittelu
                                                      #{::h/id}
                                                      {::h/hintaryhma? true})))]
    (specql/update! db
                    ::h/hinnoittelu<->toimenpide
                    {::m/poistettu? true
                     ::m/poistaja-id (:id user)}
                    {::h/toimenpide-id (op/in toimenpide-idt)
                     ::h/hinnoittelu-id (op/in hintaryhma-idt)})))

(defn poista-toimenpiteet-omista-hinnoitteluista! [db user toimenpide-idt]
  (let [toimenpiteiden-hinnoittelu-idt (set (map ::h/hinnoittelu-id
                                                 (specql/fetch db
                                                               ::h/hinnoittelu<->toimenpide
                                                               #{::h/hinnoittelu-id}
                                                               {::h/toimenpide-id (op/in toimenpide-idt)
                                                                ::m/poistettu? false})))
        oma-hinnoittelu-idt (set (map ::h/id (specql/fetch db
                                                           ::h/hinnoittelu
                                                           #{::h/id}
                                                           {::h/hintaryhma? false
                                                            ::m/poistettu? false
                                                            ::h/id (op/in toimenpiteiden-hinnoittelu-idt)})))]
    (specql/update! db
                    ::h/hinnoittelu<->toimenpide
                    {::m/poistettu? true
                     ::m/poistaja-id (:id user)}
                    {::h/toimenpide-id (op/in toimenpide-idt)
                     ::h/hinnoittelu-id (op/in oma-hinnoittelu-idt)})

    (specql/update! db
                    ::h/hinnoittelu
                    {::m/poistettu? true
                     ::m/poistaja-id (:id user)}
                    {::h/id (op/in oma-hinnoittelu-idt)})

    (specql/update! db
                    ::hinta/hinta
                    {::m/poistettu? true
                     ::m/poistaja-id (:id user)}
                    {::hinta/hinnoittelu-id (op/in oma-hinnoittelu-idt)})

    (specql/update! db
                    ::tyo/tyo
                    {::m/poistettu? true
                     ::m/poistaja-id (:id user)}
                    {::tyo/hinnoittelu-id (op/in oma-hinnoittelu-idt)})))

(defn liita-toimenpiteet-hinnoitteluun! [db user toimenpide-idt hinnoittelu]
  (doall
    (for [id toimenpide-idt]
      (specql/insert! db
                      ::h/hinnoittelu<->toimenpide
                      {::h/toimenpide-id id
                       ::h/hinnoittelu-id hinnoittelu
                       ::m/luoja-id (:id user)}))))

(defn tallenna-hintaryhmalle-hinta! [db user hinnoittelu-id hinnat]
  (doseq [hinta hinnat]
    (if (id-olemassa? (::hinta/id hinta))
      (specql/update! db
                      ::hinta/hinta
                      (merge
                        hinta
                        {::hinta/hinnoittelu-id hinnoittelu-id
                         ::m/muokkaaja-id (:id user)
                         ::m/muokattu (pvm/nyt)})
                      {::hinta/id (::hinta/id hinta)})

      (specql/insert! db
                      ::hinta/hinta
                      (merge
                        hinta
                        {::hinta/hinnoittelu-id hinnoittelu-id
                         ::m/luoja-id (:id user)})))))

(defn hae-toimenpiteen-oma-hinnoittelu [db toimenpide-id]
  (::to/oma-hinnoittelu (first (to-q/hae-hinnoittelutiedot-toimenpiteille db #{toimenpide-id}))))

(defn luo-toimenpiteelle-oma-hinnoittelu-jos-puuttuu [db user toimenpide-id urakka-id]
  (if-let [hinnoittelu-id (::h/id (hae-toimenpiteen-oma-hinnoittelu db toimenpide-id))]
    hinnoittelu-id
    (let [hinnoittelu (specql/insert! db
                                      ::h/hinnoittelu
                                      {::h/urakka-id urakka-id
                                       ::h/hintaryhma? false
                                       ::m/luoja-id (:id user)})]
      (specql/insert! db
                      ::h/hinnoittelu<->toimenpide
                      {::h/hinnoittelu-id (::h/id hinnoittelu)
                       ::h/toimenpide-id toimenpide-id
                       ::m/luoja-id (:id user)})

      (::h/id hinnoittelu))))

(defn tallenna-toimenpiteen-omat-hinnat! [{:keys [db user hinnoittelu-id hinnat]}]
  (doseq [hinta hinnat]
    (if (id-olemassa? (::hinta/id hinta))
      (specql/update! db
                      ::hinta/hinta
                      (merge
                        hinta
                        (if (::m/poistettu? hinta)
                          {::m/poistettu? true
                           ::m/poistaja-id (:id user)}
                          {::m/muokattu (pvm/nyt)
                           ::m/muokkaaja-id (:id user)}))
                      {::hinta/id (::hinta/id hinta)})

      (specql/insert! db
                      ::hinta/hinta
                      (merge
                        (dissoc hinta ::hinta/id)
                        {::m/luotu (pvm/nyt)
                         ::m/luoja-id (:id user)
                         ::hinta/hinnoittelu-id hinnoittelu-id})))))

(defn tallenna-toimenpiteen-tyot! [{:keys [db user hinnoittelu-id tyot]}]
  (doseq [tyo tyot]
    (if (id-olemassa? (::tyo/id tyo))
      (specql/update! db
                      ::tyo/tyo
                      (merge
                        {::tyo/toimenpidekoodi-id (::tyo/toimenpidekoodi-id tyo)
                         ::tyo/maara (::tyo/maara tyo)}
                        (if (::m/poistettu? tyo)
                          {::m/poistettu? true
                           ::m/poistaja-id (:id user)}
                          {::m/muokattu (pvm/nyt)
                           ::m/muokkaaja-id (:id user)}))
                      {::tyo/id (::tyo/id tyo)})
      (specql/insert! db
                      ::tyo/tyo
                      (merge
                        (dissoc tyo ::tyo/id)
                        {::tyo/hinnoittelu-id hinnoittelu-id
                        ::m/luotu (pvm/nyt)
                        ::m/luoja-id (:id user)})))))

(defn lisaa-kommentti! [db user tila kommentti pvm hinnoittelu-id]
  ;; Laskutusluvan voi antaa nykyiselle tai seuraaville kuukausille
  (when pvm
    (assert
      (to-q/laskutuspvm-nyt-tai-tulevaisuudessa? (t/now) pvm)
      "Laskutusluvan pitää olla tässä kuussa tai tulevaisuudessa"))

  (specql/insert! db
                  ::kommentti/hinnoittelun-kommentti
                  {::kommentti/aika (pvm/nyt)
                   ::kommentti/kommentti kommentti
                   ::kommentti/tila tila
                   ::kommentti/laskutus-pvm pvm
                   ::kommentti/kayttaja-id (:id user)
                   ::kommentti/hinnoittelu-id hinnoittelu-id}))
