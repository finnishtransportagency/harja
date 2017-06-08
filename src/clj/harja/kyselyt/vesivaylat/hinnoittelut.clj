(ns harja.kyselyt.vesivaylat.hinnoittelut
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.future :refer :all]
            [clojure.set :as set]
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
            [harja.domain.muokkaustiedot :as m]))

(defn vaadi-hinnoittelut-kuuluvat-urakkaan [db hinnoittelu-idt urakka-id]
  (when-not (->> (specql/fetch
                   db
                   ::h/hinnoittelu
                   (set/union h/perustiedot)
                   {::h/id (op/in hinnoittelu-idt)})
                 (keep ::h/urakka-id)
                 (every? (partial = urakka-id)))
    (throw (SecurityException. (str "Hinnoittelut " hinnoittelu-idt " eivät kuulu urakkaan " urakka-id)))))

(defn vaadi-hinnat-kuuluvat-toimenpiteeseen [db hinta-idt toimenpide-id]
  (let [toimenpiteen-hinnat (->> (specql/fetch
                                   db
                                   ::to/reimari-toimenpide
                                   (set/union to/perustiedot)
                                   {::to/id toimenpide-id})
                                 (mapcat ::to/hinnoittelu-linkit)
                                 (mapcat (comp ::h/hinnat ::h/hinnoittelut))
                                 (map ::hinta/id)
                                 (into #{}))]
    (when-not (set/subset? (set hinta-idt) toimenpiteen-hinnat)
      (throw (SecurityException. (str "Hinnat " hinta-idt " eivät kuulu toimenpiteeseen " toimenpide-id))))))

(defn vaadi-hinnat-kuuluvat-hinnoitteluun [db hinta-idt hinnoittelu-id]
  (when-not (->> (specql/fetch
                   db
                   ::h/hinnoittelu
                   (set/union h/hinnat)
                   {::h/hinnat {::hinta/id (op/in hinta-idt)}})

                 (keep ::h/id)
                 (every? (partial = hinnoittelu-id)))
    (throw (SecurityException. (str "Hinnat " hinta-idt " eivät kuulu hinnoiteluun " hinnoittelu-id)))))

(defn hae-hinnoittelut [db tiedot]
  (let
    [urakka-id (::ur/id tiedot)]
    (specql/fetch db
                  ::h/hinnoittelu
                  h/perustiedot
                  {::h/urakka-id urakka-id
                   ::h/hintaryhma? true
                   ::m/poistettu? false})))

(defn luo-hinnoittelu! [db user tiedot]
  (let
    [urakka-id (::ur/id tiedot)
     nimi (::h/nimi tiedot)]
    (specql/insert! db
                    ::h/hinnoittelu
                    {::h/urakka-id urakka-id
                     ::h/nimi nimi
                     ::h/hintaryhma? true
                     ::m/luoja-id (:id user)})))

(defn poista-toimenpiteet-hintaryhmistaan! [db user toimenpide-idt]
  (specql/update! db
                  ::h/hinnoittelu<->toimenpide
                  {::m/poistettu? true
                   ::m/poistaja-id (:id user)}
                  {::h/toimenpide-id (op/in toimenpide-idt)}))



(defn liita-toimenpiteet-hinnoitteluun! [db user toimenpide-idt hinnoittelu]
  (jdbc/with-db-transaction [db db]
    (doall
      (for [id toimenpide-idt]
       (specql/insert! db
                       ::h/hinnoittelu<->toimenpide
                       {::h/toimenpide-id id
                        ::h/hinnoittelu-id hinnoittelu
                        ::m/luoja-id (:id user)})))))

(defn tallenna-hintaryhmalle-hinta! [db user hinnoittelu-id hinnat]
  (jdbc/with-db-transaction [db db]
    (doseq [hinta hinnat]
      (if (id-olemassa? (::hinta/id hinta))
        (specql/update! db
                        ::hinta/hinta
                        (merge
                          hinta
                          {::hinta/hinnoittelu-id hinnoittelu-id
                           ::m/muokkaaja-id (:id user)
                           ::m/muokattu (pvm/nyt)})
                        {::h/id (::h/id hinta)})

        (specql/insert! db
                        ::hinta/hinta
                        (merge
                          hinta
                          {::hinta/hinnoittelu-id hinnoittelu-id
                           ::m/luoja-id (:id user)}))))))

(defn hae-toimenpiteen-oma-hinnoittelu [db toimenpide-id]
  (::to/oma-hinnoittelu (to/toimenpide-idlla (to-q/hae-hinnoittelutiedot-toimenpiteille db #{toimenpide-id})
                                             toimenpide-id)))

(defn luo-toimenpiteelle-oma-hinnoittelu [db user toimenpide-id urakka-id]
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

    hinnoittelu))

(defn tallenna-toimenpiteelle-hinta! [db user toimenpide-id hinnat urakka-id]
  (jdbc/with-db-transaction [db db]
    (let [hinnoittelu-id (::h/id
                           (if-let [hinnoittelu (hae-toimenpiteen-oma-hinnoittelu db toimenpide-id)]
                             hinnoittelu
                             (luo-toimenpiteelle-oma-hinnoittelu db user toimenpide-id urakka-id)))]
      (doseq [hinta hinnat]
        (if (id-olemassa? (::hinta/id hinta))
          (specql/update! db
                          ::hinta/hinta
                          (merge
                            hinta
                            ;; Jos määrä on tyhjä tai 0, merkataan hinta poistetuksi
                            (if ((some-fn nil? zero?) (::hinta/maara hinta))
                              {::m/poistettu? true
                               ::m/poistaja-id (:id user)}
                              {::m/muokattu (pvm/nyt)
                               ::m/muokkaaja-id (:id user)}))
                          {::hinta/id (::hinta/id hinta)})

          (specql/insert! db
                          ::hinta/hinta
                          (merge
                            hinta
                            {::m/luotu (pvm/nyt)
                             ::m/luoja-id (:id user)
                             ::hinta/hinnoittelu-id hinnoittelu-id})))))

    (hae-toimenpiteen-oma-hinnoittelu db toimenpide-id)))