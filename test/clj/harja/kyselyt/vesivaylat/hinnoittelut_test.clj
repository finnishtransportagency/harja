(ns harja.kyselyt.vesivaylat.hinnoittelut-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.vesivaylat.hinnoittelut :as q]

            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.vesivaylat.toimenpide :as to]))

(deftest vaadi-hinnoittelut-kuuluvat-urakkaan
  (is (nil? (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
              [{::h/urakka-id 1 ::h/id 1}]
              #{1}
              1)))

  (is (nil? (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
              [{::h/urakka-id 1 ::h/id 1}
               {::h/urakka-id 1 ::h/id 2}
               {::h/urakka-id 1 ::h/id 3}]
              #{1 2 3}
              1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
              [{::h/urakka-id 1 ::h/id 1}
               {::h/urakka-id nil ::h/id 4}
               {::h/urakka-id 1 ::h/id 2}
               {::h/urakka-id 1 ::h/id 3}]
              #{1 2 3 4}
              1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
                 [{::h/urakka-id 2 ::h/id 1}]
                 #{1}
                 1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
                 [{::h/urakka-id 1 ::h/id 1}
                  {::h/urakka-id 2 ::h/id 2}
                  {::h/urakka-id 1 ::h/id 3}]
                 #{1 2 3}
                 1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
                 [{::h/urakka-id 1 ::h/id 1}
                  {::h/urakka-id nil ::h/id 2}
                  {::h/urakka-id 1 ::h/id 3}]
                 #{1 2 3}
                 1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
                 [{::h/urakka-id 1 ::h/id 1}
                  {::h/urakka-id nil ::h/id 2}
                  {::h/urakka-id 1 ::h/id 3}]
                 #{1 2 3}
                 nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnoittelut-kuuluvat-urakkaan*
                 [{::h/id 1}
                  {::h/id 2}
                  {::h/id 3}]
                 #{1 2 3}
                 nil))))

(deftest vaadi-hinnat-kuuluvat-toimenpiteeseen
  (is (nil? (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/hinnat [{::hinta/id 1}]}}]}]
              #{1}
              ;; Viimeinen parametri käytössä vain lokituksessa
              nil)))

  (is (nil? (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/hinnat [{::hinta/id 1}
                                                       {::hinta/id 2}]}}]}]
              #{1}
              nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/hinnat [{::hinta/id 1}]}}]}]
              #{1 2}
              nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
                 [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                             {::h/hinnat [{::hinta/id nil}]}}]}]
                 #{1}
                 nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
                 [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                             {::h/hinnat [{::hinta/id 1}]}}]}]
                 #{nil}
                 nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-toimenpiteeseen*
                 [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                             {::h/hinnat [{::hinta/id nil}]}}]}]
                 #{nil}
                 nil))))

(deftest vaadi-tyot-kuuluvat-toimenpiteeseen
  (is (nil? (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/id 1}}]}]
              [{::tyo/hinnoittelu-id 1}]
              ;; Viimeiset parametrit ovat vain lokitusta varten
              nil
              nil)))

  (is (nil? (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/id 1}}
                                         {::h/hinnoittelut
                                          {::h/id 2}}]}]
              [{::tyo/hinnoittelu-id 1}]
              ;; Viimeiset parametrit ovat vain lokitusta varten
              nil
              nil)))

  (is (thrown?
        SecurityException
        (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
              [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                          {::h/id 1}}]}]
              [{::tyo/hinnoittelu-id 1}
               {::tyo/hinnoittelu-id 2}]
              nil
              nil)))

  (is (thrown?
        SecurityException
        (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
          [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                      {::h/id 1}}]}]
          [{::tyo/hinnoittelu-id nil}]
          nil
          nil)))

  (is (thrown?
        SecurityException
        (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
          [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                      {::h/id nil}}]}]
          [{::tyo/hinnoittelu-id 1}]
          nil
          nil)))

  (is (thrown?
        SecurityException
        (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
          [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                      {::h/id nil}}]}]
          [{::tyo/hinnoittelu-id nil}]
          nil
          nil)))

  (is (thrown?
        SecurityException
        (#'q/vaadi-tyot-kuuluvat-toimenpiteeseen*
          [{::to/hinnoittelu-linkit [{::h/hinnoittelut
                                      {::h/id 1}}]}]
          [{::tyo/hinnoittelu-id 1}
           {::tyo/hinnoittelu-id nil}]
          nil
          nil))))

(deftest vaadi-hinnat-kuuluvat-hinnoitteluun
  (is (nil? (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
              [{::hinta/hinnoittelu-id 1}]
              nil ;; lokitusta..
              1)))

  (is (nil? (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
              [{::hinta/hinnoittelu-id 1}
               {::hinta/hinnoittelu-id 1}]
              nil
              1)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id 1}]
                 nil
                 2)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id 1}
                  {::hinta/hinnoittelu-id 2}]
                 nil
                 2)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id 3}]
                 nil
                 2)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id 3}]
                 nil
                 nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id nil}]
                 nil
                 2)))

  (is (thrown? SecurityException
               (#'q/vaadi-hinnat-kuuluvat-hinnoitteluun*
                 [{::hinta/hinnoittelu-id nil}]
                 nil
                 nil))))

(deftest hinnoitteluun-kuuluu-toimenpiteita?
  (is (true? (q/hinnoitteluun-kuuluu-toimenpiteita?* [1 2 3])))
  (is (false? (q/hinnoitteluun-kuuluu-toimenpiteita?* []))))

