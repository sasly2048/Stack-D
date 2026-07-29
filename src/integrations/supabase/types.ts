export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      achievements: {
        Row: {
          created_at: string
          description: string
          icon: string
          id: string
          name: string
          sort_order: number
          tier: string
          xp_reward: number
        }
        Insert: {
          created_at?: string
          description: string
          icon?: string
          id: string
          name: string
          sort_order?: number
          tier?: string
          xp_reward?: number
        }
        Update: {
          created_at?: string
          description?: string
          icon?: string
          id?: string
          name?: string
          sort_order?: number
          tier?: string
          xp_reward?: number
        }
        Relationships: []
      }
      activity_events: {
        Row: {
          created_at: string
          id: string
          kind: string
          payload: Json
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          kind: string
          payload?: Json
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          kind?: string
          payload?: Json
          user_id?: string
        }
        Relationships: []
      }
      auth_alerts: {
        Row: {
          created_at: string
          details: Json
          failure_count: number
          id: string
          kind: string
          subject: string
        }
        Insert: {
          created_at?: string
          details?: Json
          failure_count?: number
          id?: string
          kind: string
          subject: string
        }
        Update: {
          created_at?: string
          details?: Json
          failure_count?: number
          id?: string
          kind?: string
          subject?: string
        }
        Relationships: []
      }
      auth_attempts: {
        Row: {
          at: string
          email: string | null
          id: string
          ip: string | null
          provider: string
          reason: string | null
          success: boolean
          user_agent: string | null
        }
        Insert: {
          at?: string
          email?: string | null
          id?: string
          ip?: string | null
          provider: string
          reason?: string | null
          success?: boolean
          user_agent?: string | null
        }
        Update: {
          at?: string
          email?: string | null
          id?: string
          ip?: string | null
          provider?: string
          reason?: string | null
          success?: boolean
          user_agent?: string | null
        }
        Relationships: []
      }
      breaks: {
        Row: {
          at: string
          display_name: string
          id: string
          reason: string
          room_id: string
          severity: Database["public"]["Enums"]["breach_severity"]
          user_id: string
        }
        Insert: {
          at?: string
          display_name: string
          id?: string
          reason: string
          room_id: string
          severity?: Database["public"]["Enums"]["breach_severity"]
          user_id: string
        }
        Update: {
          at?: string
          display_name?: string
          id?: string
          reason?: string
          room_id?: string
          severity?: Database["public"]["Enums"]["breach_severity"]
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "breaks_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      challenge_progress: {
        Row: {
          challenge_id: string
          completed_at: string | null
          period_start: string
          progress: number
          updated_at: string
          user_id: string
        }
        Insert: {
          challenge_id: string
          completed_at?: string | null
          period_start: string
          progress?: number
          updated_at?: string
          user_id: string
        }
        Update: {
          challenge_id?: string
          completed_at?: string | null
          period_start?: string
          progress?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "challenge_progress_challenge_id_fkey"
            columns: ["challenge_id"]
            isOneToOne: false
            referencedRelation: "challenges"
            referencedColumns: ["id"]
          },
        ]
      }
      challenges: {
        Row: {
          cadence: string
          description: string
          id: string
          metric: string
          name: string
          sort_order: number
          target: number
          xp_reward: number
        }
        Insert: {
          cadence: string
          description: string
          id: string
          metric: string
          name: string
          sort_order?: number
          target: number
          xp_reward?: number
        }
        Update: {
          cadence?: string
          description?: string
          id?: string
          metric?: string
          name?: string
          sort_order?: number
          target?: number
          xp_reward?: number
        }
        Relationships: []
      }
      email_send_log: {
        Row: {
          created_at: string
          error_message: string | null
          id: string
          message_id: string | null
          metadata: Json | null
          recipient_email: string
          status: string
          template_name: string
        }
        Insert: {
          created_at?: string
          error_message?: string | null
          id?: string
          message_id?: string | null
          metadata?: Json | null
          recipient_email: string
          status: string
          template_name: string
        }
        Update: {
          created_at?: string
          error_message?: string | null
          id?: string
          message_id?: string | null
          metadata?: Json | null
          recipient_email?: string
          status?: string
          template_name?: string
        }
        Relationships: []
      }
      email_send_state: {
        Row: {
          auth_email_ttl_minutes: number
          batch_size: number
          id: number
          retry_after_until: string | null
          send_delay_ms: number
          transactional_email_ttl_minutes: number
          updated_at: string
        }
        Insert: {
          auth_email_ttl_minutes?: number
          batch_size?: number
          id?: number
          retry_after_until?: string | null
          send_delay_ms?: number
          transactional_email_ttl_minutes?: number
          updated_at?: string
        }
        Update: {
          auth_email_ttl_minutes?: number
          batch_size?: number
          id?: number
          retry_after_until?: string | null
          send_delay_ms?: number
          transactional_email_ttl_minutes?: number
          updated_at?: string
        }
        Relationships: []
      }
      email_unsubscribe_tokens: {
        Row: {
          created_at: string
          email: string
          id: string
          token: string
          used_at: string | null
        }
        Insert: {
          created_at?: string
          email: string
          id?: string
          token: string
          used_at?: string | null
        }
        Update: {
          created_at?: string
          email?: string
          id?: string
          token?: string
          used_at?: string | null
        }
        Relationships: []
      }
      focus_groups: {
        Row: {
          active_session_code: string | null
          active_session_expires_at: string | null
          active_session_id: string | null
          active_session_started_at: string | null
          created_at: string
          created_by: string
          id: string
          name: string
          total_group_xp: number
          updated_at: string
        }
        Insert: {
          active_session_code?: string | null
          active_session_expires_at?: string | null
          active_session_id?: string | null
          active_session_started_at?: string | null
          created_at?: string
          created_by: string
          id?: string
          name: string
          total_group_xp?: number
          updated_at?: string
        }
        Update: {
          active_session_code?: string | null
          active_session_expires_at?: string | null
          active_session_id?: string | null
          active_session_started_at?: string | null
          created_at?: string
          created_by?: string
          id?: string
          name?: string
          total_group_xp?: number
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "focus_groups_active_session_id_fkey"
            columns: ["active_session_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "focus_groups_created_by_fkey"
            columns: ["created_by"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      focus_history: {
        Row: {
          breaches_count: number
          created_at: string
          duration_seconds: number
          id: string
          notes: string | null
          profile_id: string
          room_id: string | null
          score: number
          tags: string[]
          tier: string
          xp_earned: number
        }
        Insert: {
          breaches_count?: number
          created_at?: string
          duration_seconds?: number
          id?: string
          notes?: string | null
          profile_id: string
          room_id?: string | null
          score: number
          tags?: string[]
          tier?: string
          xp_earned?: number
        }
        Update: {
          breaches_count?: number
          created_at?: string
          duration_seconds?: number
          id?: string
          notes?: string | null
          profile_id?: string
          room_id?: string | null
          score?: number
          tags?: string[]
          tier?: string
          xp_earned?: number
        }
        Relationships: [
          {
            foreignKeyName: "focus_history_profile_id_fkey"
            columns: ["profile_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "focus_history_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      friendships: {
        Row: {
          addressee_id: string
          created_at: string
          id: string
          requester_id: string
          status: string
          updated_at: string
        }
        Insert: {
          addressee_id: string
          created_at?: string
          id?: string
          requester_id: string
          status?: string
          updated_at?: string
        }
        Update: {
          addressee_id?: string
          created_at?: string
          id?: string
          requester_id?: string
          status?: string
          updated_at?: string
        }
        Relationships: []
      }
      group_members: {
        Row: {
          group_id: string
          id: string
          joined_at: string
          profile_id: string
        }
        Insert: {
          group_id: string
          id?: string
          joined_at?: string
          profile_id: string
        }
        Update: {
          group_id?: string
          id?: string
          joined_at?: string
          profile_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "group_members_group_id_fkey"
            columns: ["group_id"]
            isOneToOne: false
            referencedRelation: "focus_groups"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "group_members_profile_id_fkey"
            columns: ["profile_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["id"]
          },
        ]
      }
      login_streaks: {
        Row: {
          last_claim_date: string | null
          streak: number
          total_claims: number
          updated_at: string
          user_id: string
        }
        Insert: {
          last_claim_date?: string | null
          streak?: number
          total_claims?: number
          updated_at?: string
          user_id: string
        }
        Update: {
          last_claim_date?: string | null
          streak?: number
          total_claims?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      memory_vault_items: {
        Row: {
          ai_summary: string | null
          attachments: Json
          body: string | null
          created_at: string
          history_id: string | null
          id: string
          tags: string[]
          title: string
          updated_at: string
          url: string | null
          user_id: string
        }
        Insert: {
          ai_summary?: string | null
          attachments?: Json
          body?: string | null
          created_at?: string
          history_id?: string | null
          id?: string
          tags?: string[]
          title: string
          updated_at?: string
          url?: string | null
          user_id: string
        }
        Update: {
          ai_summary?: string | null
          attachments?: Json
          body?: string | null
          created_at?: string
          history_id?: string | null
          id?: string
          tags?: string[]
          title?: string
          updated_at?: string
          url?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "memory_vault_items_history_id_fkey"
            columns: ["history_id"]
            isOneToOne: false
            referencedRelation: "focus_history"
            referencedColumns: ["id"]
          },
        ]
      }
      mentor_relationships: {
        Row: {
          created_at: string
          id: string
          mentee_id: string
          mentor_id: string
          status: string
        }
        Insert: {
          created_at?: string
          id?: string
          mentee_id: string
          mentor_id: string
          status?: string
        }
        Update: {
          created_at?: string
          id?: string
          mentee_id?: string
          mentor_id?: string
          status?: string
        }
        Relationships: []
      }
      participants: {
        Row: {
          breach_at: string | null
          breach_reason: string | null
          breached: boolean
          display_name: string
          id: string
          integrity: number
          joined_at: string
          last_heartbeat: string
          left_at: string | null
          room_id: string
          user_id: string
        }
        Insert: {
          breach_at?: string | null
          breach_reason?: string | null
          breached?: boolean
          display_name: string
          id?: string
          integrity?: number
          joined_at?: string
          last_heartbeat?: string
          left_at?: string | null
          room_id: string
          user_id: string
        }
        Update: {
          breach_at?: string | null
          breach_reason?: string | null
          breached?: boolean
          display_name?: string
          id?: string
          integrity?: number
          joined_at?: string
          last_heartbeat?: string
          left_at?: string | null
          room_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "participants_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      profiles: {
        Row: {
          avatar_url: string | null
          banner_gradient: string | null
          banner_url: string | null
          best_streak: number
          bio: string | null
          created_at: string
          current_focus_streak: number
          display_name: string
          id: string
          last_active_at: string | null
          lifetime_xp: number
          pinned_showcase: Json
          prestige_level: number
          productivity_dna: string | null
          title: string | null
          total_focus_seconds: number
          updated_at: string
        }
        Insert: {
          avatar_url?: string | null
          banner_gradient?: string | null
          banner_url?: string | null
          best_streak?: number
          bio?: string | null
          created_at?: string
          current_focus_streak?: number
          display_name?: string
          id: string
          last_active_at?: string | null
          lifetime_xp?: number
          pinned_showcase?: Json
          prestige_level?: number
          productivity_dna?: string | null
          title?: string | null
          total_focus_seconds?: number
          updated_at?: string
        }
        Update: {
          avatar_url?: string | null
          banner_gradient?: string | null
          banner_url?: string | null
          best_streak?: number
          bio?: string | null
          created_at?: string
          current_focus_streak?: number
          display_name?: string
          id?: string
          last_active_at?: string | null
          lifetime_xp?: number
          pinned_showcase?: Json
          prestige_level?: number
          productivity_dna?: string | null
          title?: string | null
          total_focus_seconds?: number
          updated_at?: string
        }
        Relationships: []
      }
      rate_limits: {
        Row: {
          hits: string[]
          key: string
          updated_at: string
        }
        Insert: {
          hits?: string[]
          key: string
          updated_at?: string
        }
        Update: {
          hits?: string[]
          key?: string
          updated_at?: string
        }
        Relationships: []
      }
      room_events: {
        Row: {
          actor_id: string | null
          actor_name: string | null
          created_at: string
          id: string
          kind: string
          payload: Json
          room_id: string
        }
        Insert: {
          actor_id?: string | null
          actor_name?: string | null
          created_at?: string
          id?: string
          kind: string
          payload?: Json
          room_id: string
        }
        Update: {
          actor_id?: string | null
          actor_name?: string | null
          created_at?: string
          id?: string
          kind?: string
          payload?: Json
          room_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "room_events_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      room_join_requests: {
        Row: {
          created_at: string
          display_name: string
          id: string
          message: string | null
          responded_at: string | null
          room_id: string
          status: string
          user_id: string
        }
        Insert: {
          created_at?: string
          display_name: string
          id?: string
          message?: string | null
          responded_at?: string | null
          room_id: string
          status?: string
          user_id: string
        }
        Update: {
          created_at?: string
          display_name?: string
          id?: string
          message?: string | null
          responded_at?: string | null
          room_id?: string
          status?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "room_join_requests_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      room_milestones: {
        Row: {
          id: string
          kind: string
          label: string
          payload: Json
          reached_at: string
          room_id: string
        }
        Insert: {
          id?: string
          kind: string
          label: string
          payload?: Json
          reached_at?: string
          room_id: string
        }
        Update: {
          id?: string
          kind?: string
          label?: string
          payload?: Json
          reached_at?: string
          room_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "room_milestones_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      room_moderators: {
        Row: {
          granted_at: string
          id: string
          room_id: string
          user_id: string
        }
        Insert: {
          granted_at?: string
          id?: string
          room_id: string
          user_id: string
        }
        Update: {
          granted_at?: string
          id?: string
          room_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "room_moderators_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      room_scheduled_events: {
        Row: {
          created_at: string
          created_by: string
          description: string | null
          duration_minutes: number
          id: string
          room_id: string
          starts_at: string
          title: string
        }
        Insert: {
          created_at?: string
          created_by: string
          description?: string | null
          duration_minutes?: number
          id?: string
          room_id: string
          starts_at: string
          title: string
        }
        Update: {
          created_at?: string
          created_by?: string
          description?: string | null
          duration_minutes?: number
          id?: string
          room_id?: string
          starts_at?: string
          title?: string
        }
        Relationships: [
          {
            foreignKeyName: "room_scheduled_events_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      room_templates: {
        Row: {
          banner_tone: string
          created_at: string
          description: string
          key: string
          sort_order: number
          target_duration_seconds: number
          title: string
          visibility: string
        }
        Insert: {
          banner_tone?: string
          created_at?: string
          description: string
          key: string
          sort_order?: number
          target_duration_seconds: number
          title: string
          visibility?: string
        }
        Update: {
          banner_tone?: string
          created_at?: string
          description?: string
          key?: string
          sort_order?: number
          target_duration_seconds?: number
          title?: string
          visibility?: string
        }
        Relationships: []
      }
      rooms: {
        Row: {
          banner_url: string | null
          code: string
          collective_goal_seconds: number | null
          collective_seconds: number
          created_at: string
          description: string | null
          ended_at: string | null
          host_id: string
          id: string
          pinned_message: string | null
          shared_goal_hours: number | null
          started_at: string | null
          status: Database["public"]["Enums"]["room_status"]
          target_duration_seconds: number
          template_key: string | null
          title: string | null
          updated_at: string
          visibility: string
        }
        Insert: {
          banner_url?: string | null
          code: string
          collective_goal_seconds?: number | null
          collective_seconds?: number
          created_at?: string
          description?: string | null
          ended_at?: string | null
          host_id: string
          id?: string
          pinned_message?: string | null
          shared_goal_hours?: number | null
          started_at?: string | null
          status?: Database["public"]["Enums"]["room_status"]
          target_duration_seconds?: number
          template_key?: string | null
          title?: string | null
          updated_at?: string
          visibility?: string
        }
        Update: {
          banner_url?: string | null
          code?: string
          collective_goal_seconds?: number | null
          collective_seconds?: number
          created_at?: string
          description?: string | null
          ended_at?: string | null
          host_id?: string
          id?: string
          pinned_message?: string | null
          shared_goal_hours?: number | null
          started_at?: string | null
          status?: Database["public"]["Enums"]["room_status"]
          target_duration_seconds?: number
          template_key?: string | null
          title?: string | null
          updated_at?: string
          visibility?: string
        }
        Relationships: []
      }
      season_participants: {
        Row: {
          season_id: string
          updated_at: string
          user_id: string
          xp: number
        }
        Insert: {
          season_id: string
          updated_at?: string
          user_id: string
          xp?: number
        }
        Update: {
          season_id?: string
          updated_at?: string
          user_id?: string
          xp?: number
        }
        Relationships: [
          {
            foreignKeyName: "season_participants_season_id_fkey"
            columns: ["season_id"]
            isOneToOne: false
            referencedRelation: "seasons"
            referencedColumns: ["id"]
          },
        ]
      }
      seasons: {
        Row: {
          description: string | null
          ends_at: string
          id: string
          name: string
          reward_title_id: string | null
          starts_at: string
          xp_multiplier: number
        }
        Insert: {
          description?: string | null
          ends_at: string
          id?: string
          name: string
          reward_title_id?: string | null
          starts_at: string
          xp_multiplier?: number
        }
        Update: {
          description?: string | null
          ends_at?: string
          id?: string
          name?: string
          reward_title_id?: string | null
          starts_at?: string
          xp_multiplier?: number
        }
        Relationships: [
          {
            foreignKeyName: "seasons_reward_title_id_fkey"
            columns: ["reward_title_id"]
            isOneToOne: false
            referencedRelation: "titles"
            referencedColumns: ["id"]
          },
        ]
      }
      session_reactions: {
        Row: {
          created_at: string
          emoji: string
          id: string
          session_id: string
          user_id: string
        }
        Insert: {
          created_at?: string
          emoji: string
          id?: string
          session_id: string
          user_id: string
        }
        Update: {
          created_at?: string
          emoji?: string
          id?: string
          session_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "session_reactions_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "focus_history"
            referencedColumns: ["id"]
          },
        ]
      }
      session_workspace_items: {
        Row: {
          content: string
          created_at: string
          done: boolean
          id: string
          kind: string
          position: number
          room_id: string | null
          session_id: string | null
          updated_at: string
          url: string | null
          user_id: string
        }
        Insert: {
          content: string
          created_at?: string
          done?: boolean
          id?: string
          kind: string
          position?: number
          room_id?: string | null
          session_id?: string | null
          updated_at?: string
          url?: string | null
          user_id: string
        }
        Update: {
          content?: string
          created_at?: string
          done?: boolean
          id?: string
          kind?: string
          position?: number
          room_id?: string | null
          session_id?: string | null
          updated_at?: string
          url?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "session_workspace_items_room_id_fkey"
            columns: ["room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "session_workspace_items_session_id_fkey"
            columns: ["session_id"]
            isOneToOne: false
            referencedRelation: "focus_history"
            referencedColumns: ["id"]
          },
        ]
      }
      suppressed_emails: {
        Row: {
          created_at: string
          email: string
          id: string
          metadata: Json | null
          reason: string
        }
        Insert: {
          created_at?: string
          email: string
          id?: string
          metadata?: Json | null
          reason: string
        }
        Update: {
          created_at?: string
          email?: string
          id?: string
          metadata?: Json | null
          reason?: string
        }
        Relationships: []
      }
      time_capsules: {
        Row: {
          created_at: string
          id: string
          message: string
          open_at: string
          opened_at: string | null
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          message: string
          open_at: string
          opened_at?: string | null
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          message?: string
          open_at?: string
          opened_at?: string | null
          user_id?: string
        }
        Relationships: []
      }
      titles: {
        Row: {
          criteria: Json
          description: string
          icon: string | null
          id: string
          name: string
        }
        Insert: {
          criteria?: Json
          description: string
          icon?: string | null
          id: string
          name: string
        }
        Update: {
          criteria?: Json
          description?: string
          icon?: string | null
          id?: string
          name?: string
        }
        Relationships: []
      }
      user_achievements: {
        Row: {
          achievement_id: string
          unlocked_at: string
          user_id: string
        }
        Insert: {
          achievement_id: string
          unlocked_at?: string
          user_id: string
        }
        Update: {
          achievement_id?: string
          unlocked_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "user_achievements_achievement_id_fkey"
            columns: ["achievement_id"]
            isOneToOne: false
            referencedRelation: "achievements"
            referencedColumns: ["id"]
          },
        ]
      }
      user_blocks: {
        Row: {
          blocked_id: string
          blocker_id: string
          created_at: string
        }
        Insert: {
          blocked_id: string
          blocker_id: string
          created_at?: string
        }
        Update: {
          blocked_id?: string
          blocker_id?: string
          created_at?: string
        }
        Relationships: []
      }
      user_reports: {
        Row: {
          created_at: string
          id: string
          kind: string
          reason: string | null
          reporter_id: string
          status: string
          target_room_id: string | null
          target_user_id: string | null
        }
        Insert: {
          created_at?: string
          id?: string
          kind: string
          reason?: string | null
          reporter_id: string
          status?: string
          target_room_id?: string | null
          target_user_id?: string | null
        }
        Update: {
          created_at?: string
          id?: string
          kind?: string
          reason?: string | null
          reporter_id?: string
          status?: string
          target_room_id?: string | null
          target_user_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "user_reports_target_room_id_fkey"
            columns: ["target_room_id"]
            isOneToOne: false
            referencedRelation: "rooms"
            referencedColumns: ["id"]
          },
        ]
      }
      user_titles: {
        Row: {
          earned_at: string
          title_id: string
          user_id: string
        }
        Insert: {
          earned_at?: string
          title_id: string
          user_id: string
        }
        Update: {
          earned_at?: string
          title_id?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "user_titles_title_id_fkey"
            columns: ["title_id"]
            isOneToOne: false
            referencedRelation: "titles"
            referencedColumns: ["id"]
          },
        ]
      }
      webhook_deliveries: {
        Row: {
          attempt: number
          created_at: string
          event: string
          id: string
          ok: boolean
          response_snippet: string | null
          status_code: number | null
          user_id: string
          webhook_id: string
        }
        Insert: {
          attempt?: number
          created_at?: string
          event: string
          id?: string
          ok?: boolean
          response_snippet?: string | null
          status_code?: number | null
          user_id: string
          webhook_id: string
        }
        Update: {
          attempt?: number
          created_at?: string
          event?: string
          id?: string
          ok?: boolean
          response_snippet?: string | null
          status_code?: number | null
          user_id?: string
          webhook_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "webhook_deliveries_webhook_id_fkey"
            columns: ["webhook_id"]
            isOneToOne: false
            referencedRelation: "webhooks"
            referencedColumns: ["id"]
          },
        ]
      }
      webhooks: {
        Row: {
          active: boolean
          created_at: string
          events: string[]
          id: string
          secret: string | null
          url: string
          user_id: string
        }
        Insert: {
          active?: boolean
          created_at?: string
          events?: string[]
          id?: string
          secret?: string | null
          url: string
          user_id: string
        }
        Update: {
          active?: boolean
          created_at?: string
          events?: string[]
          id?: string
          secret?: string | null
          url?: string
          user_id?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      are_friends: { Args: { _a: string; _b: string }; Returns: boolean }
      check_and_record_hit: {
        Args: { _key: string; _max_hits: number; _window_seconds: number }
        Returns: boolean
      }
      claim_daily_reward: {
        Args: never
        Returns: {
          day_of_streak: number
          new_streak: number
          reward_xp: number
        }[]
      }
      claim_room_seat: {
        Args: { _code: string }
        Returns: {
          banner_url: string | null
          code: string
          collective_goal_seconds: number | null
          collective_seconds: number
          created_at: string
          description: string | null
          ended_at: string | null
          host_id: string
          id: string
          pinned_message: string | null
          shared_goal_hours: number | null
          started_at: string | null
          status: Database["public"]["Enums"]["room_status"]
          target_duration_seconds: number
          template_key: string | null
          title: string | null
          updated_at: string
          visibility: string
        }
        SetofOptions: {
          from: "*"
          to: "rooms"
          isOneToOne: true
          isSetofReturn: false
        }
      }
      delete_email: {
        Args: { message_id: number; queue_name: string }
        Returns: boolean
      }
      dispatch_group_sprint: {
        Args: {
          _active_session_code: string
          _active_session_id: string
          _expires_at: string
          _group_id: string
          _started_at: string
        }
        Returns: undefined
      }
      email_queue_dispatch: { Args: never; Returns: undefined }
      enqueue_email: {
        Args: { payload: Json; queue_name: string }
        Returns: number
      }
      evaluate_achievements: {
        Args: { _history_id: string; _user_id: string }
        Returns: string[]
      }
      evaluate_challenges: {
        Args: { _history_id: string; _user_id: string }
        Returns: undefined
      }
      finalize_focus_session: {
        Args: {
          _breaches_count: number
          _duration_seconds: number
          _room_id: string
          _score: number
          _tier: string
          _xp: number
        }
        Returns: string
      }
      is_group_member: {
        Args: { _group_id: string; _user_id: string }
        Returns: boolean
      }
      is_room_host: {
        Args: { _room_id: string; _user_id: string }
        Returns: boolean
      }
      is_room_moderator: {
        Args: { _room_id: string; _user_id: string }
        Returns: boolean
      }
      is_room_participant: {
        Args: { _room_id: string; _user_id: string }
        Returns: boolean
      }
      move_to_dlq: {
        Args: {
          dlq_name: string
          message_id: number
          payload: Json
          source_queue: string
        }
        Returns: number
      }
      presence_heartbeat: { Args: never; Returns: undefined }
      prestige_up: {
        Args: never
        Returns: {
          new_prestige: number
        }[]
      }
      read_email_batch: {
        Args: { batch_size: number; queue_name: string; vt: number }
        Returns: {
          message: Json
          msg_id: number
          read_ct: number
        }[]
      }
      recent_auth_failures:
        | {
            Args: { _email: string; _provider: string; _window_seconds: number }
            Returns: number
          }
        | {
            Args: {
              _email: string
              _ip?: string
              _provider: string
              _window_seconds: number
            }
            Returns: number
          }
      record_auth_alert_if_new: {
        Args: {
          _cooldown_seconds: number
          _details: Json
          _failure_count: number
          _kind: string
          _subject: string
        }
        Returns: string
      }
      record_breach: {
        Args: {
          _integrity: number
          _participant_id: string
          _reason: string
          _room_id: string
          _severity: Database["public"]["Enums"]["breach_severity"]
        }
        Returns: undefined
      }
      record_room_event: {
        Args: { _kind: string; _payload?: Json; _room_id: string }
        Returns: string
      }
      room_code_exists: { Args: { _code: string }; Returns: boolean }
      update_session_meta: {
        Args: { _history_id: string; _notes: string; _tags: string[] }
        Returns: undefined
      }
    }
    Enums: {
      breach_severity: "minor" | "severe"
      room_status: "lobby" | "active" | "complete" | "aborted"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      breach_severity: ["minor", "severe"],
      room_status: ["lobby", "active", "complete", "aborted"],
    },
  },
} as const
