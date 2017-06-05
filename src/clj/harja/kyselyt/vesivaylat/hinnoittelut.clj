(ns harja.kyselyt.vesivaylat.hinnoittelut
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [clojure.future :refer :all]
            [clojure.set :as set]
            [harja.id :refer [id-olemassa?]]

            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm]))

(defn hae-hinnoittelut [db tiedot]
  (let
    [urakka-id (::ur/id tiedot)]
    (specql/fetch db
                  ::h/hinnoittelu
                  h/perustiedot
                  {::h/urakka-id urakka-id})))

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

(defn toimenpiteet-kuuluu-urakkaan? [urakka-id]
  {::h/toimenpiteet {::to/urakka-id urakka-id}})

(defn poista-toimenpiteet-hintaryhmistaan! [db user toimenpide-idt urakka-id]
  (specql/update! db
                  ::h/hinnoittelu<->toimenpide
                  {::m/poistettu? true
                   ::m/poistaja-id (:id user)}
                  (op/and
                    {::h/toimenpide-id (op/in toimenpide-idt)}
                    (toimenpiteet-kuuluu-urakkaan? urakka-id)
                    {::h/hinnoittelut {::h/hintaryhma? true}})))

(defn liita-toimenpiteet-hinnoitteluun! [db user toimenpide-idt hinnoittelu urakka-id]
  (doseq [id toimenpide-idt]
    (specql/insert! db
                    ::h/hinnoittelu<->toimenpide
                    (op/and
                      {::h/toimenpide-id id
                       ::h/hinnoittelu-id hinnoittelu
                       ::m/luoja-id (:id user)}
                      (toimenpiteet-kuuluu-urakkaan? urakka-id)))))

(defn hinnoittelu-kuuluu-urakkaan? [db hinnoittelu-id urakka-id]
  (not-empty
    (specql/fetch
      db
      ::h/hinnoittelu
      (set/union h/perustiedot
                 h/hinnoittelun-toimenpiteet)
      (op/and
        {::h/toimenpide-linkit {::h/toimenpiteet {::to/urakka-id urakka-id}}}
        {::h/id hinnoittelu-id}))))

(defn tallenna-hintaryhmalle-hinta! [db user hinnoittelu-id hinnat urakka-id]
  (jdbc/with-db-transaction [db db]
    (when (hinnoittelu-kuuluu-urakkaan? db hinnoittelu-id urakka-id)
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
                             ::m/luoja-id (:id user)}))))

      (specql/fetch db
                    ::h/hinnoittelu
                    h/hinnoittelutiedot
                    {::h/id hinnoittelu-id}))))

(defn hae-toimenpiteen-oma-hinnoittelu [db toimenpide-id]
  (->
    (specql/fetch db
                  ::to/reimari-toimenpide
                  (set/union to/perustiedot to/hinnoittelu)
                  (op/and
                    {::to/id toimenpide-id}
                    {::to/hinnoittelu-linkit
                     {::h/hinnoittelut
                      {::h/hintaryhma? false
                       ::m/poistettu? false}}}))
    (get-in [::to/hinnoittelu-linkit ::h/hinnoittelut])))

(defn luo-toimenpiteelle-oma-hinnoittelu [db user toimenpide-id urakka-id]
  (jdbc/with-db-transaction [db db]
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

      hinnoittelu)))

(defn tallenna-toimenpiteelle-hinta! [db user toimenpide-id hinnat urakka-id]
  ;; TODO Vaadi hinta kuuluu toimenpiteeseen?
  (jdbc/with-db-transaction [db db]
    (let [hinnoittelu-id (::h/id
                           (if-let [hinnoittelu (hae-toimenpiteen-oma-hinnoittelu db toimenpide-id)]
                             (do
                               (assert
                                 (= (count hinnoittelu) 1)
                                 (str "Toimenpiteelle " toimenpide-id " löyty " (count hinnoittelu) " omaa hinnoittelua, pitää olla vain yksi"))
                               (first hinnoittelu))
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